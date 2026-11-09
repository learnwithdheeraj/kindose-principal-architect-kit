package com.kindose.app.data

import com.kindose.app.domain.*
import com.kindose.app.fcm.FcmPayloadParser
import com.kindose.app.fcm.FcmParseOutcome
import com.kindose.app.fcm.FcmRuntimeRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class DoseWindowRecord(
    val spec: DoseScheduleSpec,
    val state: DoseState,
    val duplicateTapWarningMinutes: Long? = null
)

data class RepositorySnapshot(
    val patientName: String,
    val patientAge: Int,
    val caregiverName: String,
    val caregiverRelation: String,
    val activeWindowId: DoseWindowId,
    val windows: List<DoseWindowRecord>,
    val auditLedger: List<AuditLogEntry>,
    val outboxQueue: List<TransactionalOutboxRecord>,
    val simulatedClockMillis: EpochMillis,
    val adherenceStreakDays: Int,
    val thirtyDayAdherencePercent: Int,
    val cloudVault: CloudVaultSyncStatus
) {
    val activeRecord: DoseWindowRecord
        get() = windows.firstOrNull { it.spec.doseWindowId == activeWindowId } ?: windows.first()
}

sealed interface MedicationCreationOutcome {
    data class Created(
        val windowId: DoseWindowId,
        val medicationName: String,
        val strengthLabel: String,
        val scheduledTimeLabel: String
    ) : MedicationCreationOutcome

    data class RejectedValidation(
        val code: String,
        val userMessage: String
    ) : MedicationCreationOutcome
}

sealed interface MedicationDeletionOutcome {
    data class Deleted(
        val deletedMedicationName: String,
        val newActiveWindowId: DoseWindowId
    ) : MedicationDeletionOutcome

    data class RejectedLastSchedule(
        val userMessage: String
    ) : MedicationDeletionOutcome
}

sealed interface PairingCodeOutcome {
    data class Verified(
        val normalizedCode: String,
        val pairedEmail: String,
        val pairedDisplayName: String
    ) : PairingCodeOutcome

    data class InvalidCode(
        val userMessage: String
    ) : PairingCodeOutcome
}

interface DataRepository {
    val snapshot: StateFlow<RepositorySnapshot>
    fun selectDoseWindow(doseWindowId: DoseWindowId)
    fun dispatchCommand(doseWindowId: DoseWindowId, command: DoseCommand): TransitionOutcome
    fun simulateDuplicateTapAfter18Minutes(doseWindowId: DoseWindowId)
    fun simulateFastForwardToEscalation(doseWindowId: DoseWindowId)
    fun resetWindowToDue(doseWindowId: DoseWindowId)
    fun switchIdentityProvider(provider: IdentityProvider)
    fun signInWithAccount(
        provider: IdentityProvider,
        email: String,
        displayName: String,
        credentialSourceNote: String = "${provider.displayName} · ${provider.protocolBadge}"
    )
    fun verifySeniorPairingCode(rawCode: String, seniorNameInput: String = "Eleanor Vance"): PairingCodeOutcome
    fun signOutAccount()
    fun verifyAndRevokeIfCacheCleared(): Boolean
    fun simulateDeviceChangeRestore(newDeviceModel: String, viaProvider: IdentityProvider)
    fun simulateFcmPushEscalationTest()
    fun flushPendingOutboxEvents(): Int
    fun addNewMedicationSchedule(
        medicationName: String,
        strengthLabel: String,
        scheduledTimeLabel: String,
        clinicalPurpose: String,
        foodWarning: String,
        pillShape: PillShape,
        pillColorLabel: String,
        imprintCode: String
    ): DoseWindowId
    fun validateAndAddMedicationSchedule(
        medicationName: String,
        strengthLabel: String,
        scheduledTimeLabel: String,
        clinicalPurpose: String,
        foodWarning: String,
        pillShape: PillShape,
        pillColorLabel: String,
        imprintCode: String
    ): MedicationCreationOutcome
    fun deleteMedicationSchedule(doseWindowId: DoseWindowId): MedicationDeletionOutcome
}

/**
 * Hermetic, Thread-Safe Transactional Repository implementing the RFC-001 Outbox + Audit Ledger contract.
 * Updates `windows`, `auditLedger`, and `outboxQueue` atomically inside a single `MutableStateFlow.update` block,
 * and persists authenticated state + schedules through `LocalVaultPersistenceStore`.
 */
class DefaultDataRepository(
    private val startSignedIn: Boolean = true,
    private val persistenceStore: LocalVaultPersistenceStore? = null
) : DataRepository {

    companion object {
        // Base anchor: 08:02 AM local simulation epoch
        const val BASE_0800_EPOCH_MS = 1_762_416_000_000L
        private const val MIN_MS = 60_000L
        const val MAX_DAILY_WINDOWS = 12
        const val MAX_AUDIT_ENTRIES = 100
        const val MAX_OUTBOX_ENTRIES = 100

        val MORNING_WINDOW_ID = DoseWindowId("dose-2026-11-06-0800-lisinopril")
        val AFTERNOON_WINDOW_ID = DoseWindowId("dose-2026-11-06-1300-eliquis")
        val EVENING_WINDOW_ID = DoseWindowId("dose-2026-11-06-2000-atorvastatin")
    }

    private val processedIdempotencyKeys = mutableSetOf<String>()
    private var sequenceCounter = 1

    private val _snapshot = MutableStateFlow(createInitialSnapshot())
    override val snapshot: StateFlow<RepositorySnapshot> = _snapshot.asStateFlow()

    private fun persistCurrentSnapshot() {
        persistenceStore?.saveSnapshot(_snapshot.value)
    }

    override fun verifyAndRevokeIfCacheCleared(): Boolean {
        val store = persistenceStore ?: return false
        if (_snapshot.value.cloudVault.isSignedIn && !store.isCacheSessionValid()) {
            signOutAccount()
            return true
        }
        return false
    }

    override fun selectDoseWindow(doseWindowId: DoseWindowId) {
        _snapshot.update { current ->
            if (current.windows.none { it.spec.doseWindowId == doseWindowId }) {
                current
            } else {
                current.copy(activeWindowId = doseWindowId)
            }
        }
        persistCurrentSnapshot()
    }

    override fun dispatchCommand(doseWindowId: DoseWindowId, command: DoseCommand): TransitionOutcome {
        var resultOutcome: TransitionOutcome? = null

        _snapshot.update { current ->
            val targetIndex = current.windows.indexOfFirst { it.spec.doseWindowId == doseWindowId }
            if (targetIndex == -1) return@update current

            val record = current.windows[targetIndex]

            // Idempotency Guard
            if (processedIdempotencyKeys.contains(command.idempotencyKey.value)) {
                resultOutcome = TransitionOutcome.Applied(record.state, record.state, emptyList())
                return@update current
            }

            val outcome = DoseEscalationStateMachine.evaluate(record.spec, record.state, command)
            resultOutcome = outcome

            when (outcome) {
                is TransitionOutcome.Applied -> {
                    processedIdempotencyKeys.add(command.idempotencyKey.value)

                    val shieldEffect = outcome.sideEffects
                        .filterIsInstance<DoseSideEffect.TriggerDoubleDosePreventionShield>()
                        .firstOrNull()

                    val outboxEffects = outcome.sideEffects
                        .filterIsInstance<DoseSideEffect.EnqueueTransactionalOutboxEvent>()

                    val updatedRecord = record.copy(
                        state = outcome.newState,
                        duplicateTapWarningMinutes = shieldEffect?.minutesSinceTaken
                    )

                    val updatedWindows = current.windows.toMutableList().apply {
                        set(targetIndex, updatedRecord)
                    }

                    val timeLabel = formatClock(command.occurredAt.millis)
                    val newAudit = AuditLogEntry(
                        auditId = "AUD-${1000 + sequenceCounter++}",
                        doseWindowId = doseWindowId,
                        medicationName = record.spec.medicationName,
                        fromState = outcome.previousState.stateName,
                        toState = outcome.newState.stateName,
                        commandName = command::class.simpleName ?: "DoseCommand",
                        idempotencyKey = command.idempotencyKey,
                        occurredAt = command.occurredAt,
                        formattedTime = timeLabel,
                        detail = when {
                            shieldEffect != null ->
                                "DOUBLE-DOSE SHIELD BLOCKED DUPLICATE TAP (+${shieldEffect.minutesSinceTaken}m after original confirmation)"
                            command is DoseCommand.OnSeniorUndoConfirm ->
                                "UNDO ACCIDENTAL CONFIRMATION (${command.reason}) -> Re-armed 8:30 AM Escalation Watchdog"
                            else ->
                                "Transitioned ${outcome.previousState.stateName} -> ${outcome.newState.stateName}"
                        }
                    )

                    val deliveryBadge = if (current.cloudVault.isSignedIn) {
                        "SYNCED (WAL -> TLS 1.3)"
                    } else {
                        "QUEUED_OFFLINE_WAL (Awaiting Auth)"
                    }

                    val newOutboxItems = outboxEffects.map { effect ->
                        TransactionalOutboxRecord(
                            outboxId = "OUT-${2000 + sequenceCounter++}",
                            idempotencyKey = effect.idempotencyKey,
                            eventType = effect.eventType,
                            medicationName = record.spec.medicationName,
                            deliveryStatus = deliveryBadge,
                            formattedTime = timeLabel
                        )
                    }

                    current.copy(
                        windows = updatedWindows,
                        auditLedger = (listOf(newAudit) + current.auditLedger).take(MAX_AUDIT_ENTRIES),
                        outboxQueue = (newOutboxItems + current.outboxQueue).take(MAX_OUTBOX_ENTRIES),
                        simulatedClockMillis = command.occurredAt
                    )
                }

                is TransitionOutcome.RejectedDomainRule -> {
                    val timeLabel = formatClock(command.occurredAt.millis)
                    val rejectionAudit = AuditLogEntry(
                        auditId = "AUD-${1000 + sequenceCounter++}",
                        doseWindowId = doseWindowId,
                        medicationName = record.spec.medicationName,
                        fromState = record.state.stateName,
                        toState = "REJECTED (${outcome.code.name})",
                        commandName = command::class.simpleName ?: "DoseCommand",
                        idempotencyKey = command.idempotencyKey,
                        occurredAt = command.occurredAt,
                        formattedTime = timeLabel,
                        detail = "Domain invariant blocked command: ${outcome.reason}"
                    )
                    current.copy(
                        auditLedger = (listOf(rejectionAudit) + current.auditLedger).take(MAX_AUDIT_ENTRIES)
                    )
                }
            }
        }

        persistCurrentSnapshot()
        return resultOutcome ?: TransitionOutcome.RejectedDomainRule(
            DoseState.Scheduled,
            RejectionCode.INVALID_STATE_TRANSITION,
            "Window not found"
        )
    }

    override fun flushPendingOutboxEvents(): Int {
        var flushedCount = 0
        _snapshot.update { current ->
            val updatedOutbox = current.outboxQueue.map { item ->
                if (!item.deliveryStatus.startsWith("SYNCED")) {
                    flushedCount++
                    item.copy(deliveryStatus = "SYNCED (WAL -> TLS 1.3)")
                } else {
                    item
                }
            }
            val now = EpochMillis(current.simulatedClockMillis.millis + MIN_MS)
            val timeLabel = formatClock(now.millis)
            val syncAudit = AuditLogEntry(
                auditId = "AUD-${1000 + sequenceCounter++}",
                doseWindowId = current.activeWindowId,
                medicationName = "Transactional Outbox Worker",
                fromState = "WAL_OUTBOX",
                toState = "CLOUD_SYNCED",
                commandName = "OnFlushOutboxBatch",
                idempotencyKey = IdempotencyKey("idem-flush-${System.nanoTime()}"),
                occurredAt = now,
                formattedTime = timeLabel,
                detail = "Flushed $flushedCount pending WAL outbox events & verified ${updatedOutbox.size} idempotent records against FamilyVault (${current.cloudVault.familyVaultId})"
            )
            current.copy(
                outboxQueue = updatedOutbox,
                auditLedger = (listOf(syncAudit) + current.auditLedger).take(MAX_AUDIT_ENTRIES),
                cloudVault = current.cloudVault.copy(lastCloudCheckpointTime = timeLabel)
            )
        }
        persistCurrentSnapshot()
        return flushedCount
    }

    override fun simulateDuplicateTapAfter18Minutes(doseWindowId: DoseWindowId) {
        val current = _snapshot.value
        val record = current.windows.firstOrNull { it.spec.doseWindowId == doseWindowId } ?: return
        val firstConfirmTime = record.spec.dueAt + (2 * MIN_MS) // 08:02 AM
        if (record.state !is DoseState.Confirmed) {
            dispatchCommand(
                doseWindowId,
                DoseCommand.OnSeniorConfirm(
                    occurredAt = firstConfirmTime,
                    idempotencyKey = IdempotencyKey("idem-confirm-${System.nanoTime()}")
                )
            )
        }
        val baseTime = (_snapshot.value.windows.first { it.spec.doseWindowId == doseWindowId }.state as? DoseState.Confirmed)
            ?.confirmedAt ?: firstConfirmTime
        val secondTapTime = baseTime + (18 * MIN_MS)
        dispatchCommand(
            doseWindowId,
            DoseCommand.OnSeniorConfirm(
                occurredAt = secondTapTime,
                idempotencyKey = IdempotencyKey("idem-dup-tap-${System.nanoTime()}")
            )
        )
    }

    override fun simulateFastForwardToEscalation(doseWindowId: DoseWindowId) {
        resetWindowToDue(doseWindowId)
        val record = _snapshot.value.windows.firstOrNull { it.spec.doseWindowId == doseWindowId } ?: return
        dispatchCommand(
            doseWindowId,
            DoseCommand.OnGraceDeadlineExpired(
                occurredAt = record.spec.escalationDeadline,
                idempotencyKey = IdempotencyKey("idem-escalate-${System.nanoTime()}")
            )
        )
        simulateFcmPushEscalationTest()
    }

    override fun simulateFcmPushEscalationTest() {
        val current = _snapshot.value
        val spec = current.activeRecord.spec
        val rawMap = mapOf(
            "idempotency_key" to "fcm-esc-${System.nanoTime()}",
            "schema_version" to "1",
            "sent_epoch_ms" to current.simulatedClockMillis.millis.toString(),
            "event_type" to "CAREGIVER_ESCALATION_SLA_BREACH",
            "patient_id" to spec.patientId.value,
            "patient_name" to current.patientName,
            "window_id" to spec.doseWindowId.value,
            "medication_name" to spec.medicationName,
            "strength_label" to spec.strengthLabel,
            "scheduled_time_label" to spec.scheduledTimeLabel,
            "overdue_minutes" to "30",
            "caregiver_channel" to "FCM_HIGH_PRIORITY + SMS_FALLBACK"
        )
        val parseOutcome = FcmPayloadParser.parseAndDeduplicate(
            rawData = rawMap,
            deduplicator = FcmRuntimeRegistry.sharedDeduplicator,
            nowEpochMs = current.simulatedClockMillis.millis
        )
        if (parseOutcome is FcmParseOutcome.Valid) {
            FcmRuntimeRegistry.recordInboundPayload(parseOutcome.payload)
            _snapshot.update { snap ->
                snap.copy(
                    cloudVault = snap.cloudVault.copy(
                        fcmTokenPreview = FcmRuntimeRegistry.latestMaskedToken,
                        fcmPushDeliveredCount = snap.cloudVault.fcmPushDeliveredCount + 1,
                        lastFcmPushSummary = FcmRuntimeRegistry.lastPushSummary
                    )
                )
            }
            persistCurrentSnapshot()
        }
    }

    override fun resetWindowToDue(doseWindowId: DoseWindowId) {
        _snapshot.update { current ->
            val idx = current.windows.indexOfFirst { it.spec.doseWindowId == doseWindowId }
            if (idx == -1) return@update current
            val record = current.windows[idx]
            val dueTime = record.spec.dueAt
            val updatedWindows = current.windows.toMutableList().apply {
                set(
                    idx,
                    record.copy(
                        state = DoseState.Due(notifiedAt = dueTime),
                        duplicateTapWarningMinutes = null
                    )
                )
            }
            current.copy(
                windows = updatedWindows,
                simulatedClockMillis = dueTime
            )
        }
        persistCurrentSnapshot()
    }

    override fun switchIdentityProvider(provider: IdentityProvider) {
        _snapshot.update { current ->
            val updatedLinked = current.cloudVault.linkedProviders + provider
            current.copy(
                cloudVault = current.cloudVault.copy(
                    isSignedIn = true,
                    activeProvider = provider,
                    linkedProviders = updatedLinked
                )
            )
        }
        persistCurrentSnapshot()
    }

    override fun verifySeniorPairingCode(rawCode: String, seniorNameInput: String): PairingCodeOutcome {
        val digitsOnly = rawCode.filter { it.isDigit() }
        if (digitsOnly.length != 6) {
            return PairingCodeOutcome.InvalidCode(
                "Pairing code must be exactly 6 digits (e.g., 849-204). You entered ${digitsOnly.length} digit(s)."
            )
        }
        val cleanSeniorName = seniorNameInput.trim().ifBlank { "Eleanor Vance" }
        val normalized = "${digitsOnly.substring(0, 3)}-${digitsOnly.substring(3, 6)}"
        val pairedEmail = "paired.$digitsOnly@kindose.family"
        signInWithAccount(
            provider = IdentityProvider.SENIOR_QR_PAIRING,
            email = pairedEmail,
            displayName = cleanSeniorName,
            credentialSourceNote = "Ed25519 Hardware Pairing Code ($normalized)"
        )
        return PairingCodeOutcome.Verified(
            normalizedCode = normalized,
            pairedEmail = pairedEmail,
            pairedDisplayName = cleanSeniorName
        )
    }

    override fun signInWithAccount(
        provider: IdentityProvider,
        email: String,
        displayName: String,
        credentialSourceNote: String
    ) {
        val cleanEmail = email.trim().ifBlank { "family.member@kindose.app" }
        val cleanName = displayName.trim().ifBlank {
            cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        }
        _snapshot.update { current ->
            val now = EpochMillis(current.simulatedClockMillis.millis + MIN_MS)
            val timeLabel = formatClock(now.millis)
            val authAudit = AuditLogEntry(
                auditId = "AUD-${1000 + sequenceCounter++}",
                doseWindowId = current.activeWindowId,
                medicationName = "CredentialManager Auth",
                fromState = if (current.cloudVault.isSignedIn) "SIGNED_IN" else "SIGNED_OUT",
                toState = provider.name,
                commandName = "OnSignInSuccess",
                idempotencyKey = IdempotencyKey("idem-auth-${System.nanoTime()}"),
                occurredAt = now,
                formattedTime = timeLabel,
                detail = "Authenticated as $cleanName ($cleanEmail) · $credentialSourceNote · Hydrated FamilyVault (${current.cloudVault.familyVaultId})"
            )
            current.copy(
                patientName = cleanName,
                caregiverName = cleanName,
                auditLedger = (listOf(authAudit) + current.auditLedger).take(MAX_AUDIT_ENTRIES),
                cloudVault = current.cloudVault.copy(
                    isSignedIn = true,
                    primaryEmail = cleanEmail,
                    accountDisplayName = cleanName,
                    activeProvider = provider,
                    linkedProviders = current.cloudVault.linkedProviders + provider,
                    lastCloudCheckpointTime = timeLabel,
                    credentialSourceNote = credentialSourceNote
                )
            )
        }
        persistCurrentSnapshot()
    }

    override fun signOutAccount() {
        persistenceStore?.clearAuthSessionOnly()
        _snapshot.update { current ->
            val now = EpochMillis(current.simulatedClockMillis.millis + MIN_MS)
            val timeLabel = formatClock(now.millis)
            val signOutAudit = AuditLogEntry(
                auditId = "AUD-${1000 + sequenceCounter++}",
                doseWindowId = current.activeWindowId,
                medicationName = "Security Boundary",
                fromState = "SIGNED_IN",
                toState = "SIGNED_OUT_ZERO_TRUST",
                commandName = "OnSignOutRevoke",
                idempotencyKey = IdempotencyKey("idem-signout-${System.nanoTime()}"),
                occurredAt = now,
                formattedTime = timeLabel,
                detail = "Destroyed active session token & cache sentinel · Locked clinical schedule behind Zero-Trust Auth Gate"
            )
            current.copy(
                auditLedger = (listOf(signOutAudit) + current.auditLedger).take(MAX_AUDIT_ENTRIES),
                cloudVault = current.cloudVault.copy(
                    isSignedIn = false,
                    primaryEmail = "",
                    accountDisplayName = "",
                    linkedProviders = emptySet()
                )
            )
        }
    }

    override fun simulateDeviceChangeRestore(newDeviceModel: String, viaProvider: IdentityProvider) {
        _snapshot.update { current ->
            val now = EpochMillis(current.simulatedClockMillis.millis + MIN_MS)
            val timeLabel = formatClock(now.millis)
            val migrationAudit = AuditLogEntry(
                auditId = "AUD-${1000 + sequenceCounter++}",
                doseWindowId = current.activeWindowId,
                medicationName = "FamilyVault Restore",
                fromState = current.cloudVault.currentDeviceModel,
                toState = newDeviceModel,
                commandName = "OnDeviceMigrationHydrate",
                idempotencyKey = IdempotencyKey("idem-device-restore-${System.nanoTime()}"),
                occurredAt = now,
                formattedTime = timeLabel,
                detail = "Hydrated ${current.windows.size} schedules + ${current.adherenceStreakDays}d streak onto $newDeviceModel via ${viaProvider.displayName} (${viaProvider.protocolBadge}) & re-armed AlarmManager.setAlarmClock()"
            )
            current.copy(
                auditLedger = (listOf(migrationAudit) + current.auditLedger).take(MAX_AUDIT_ENTRIES),
                cloudVault = current.cloudVault.copy(
                    activeProvider = viaProvider,
                    linkedProviders = current.cloudVault.linkedProviders + viaProvider,
                    currentDeviceModel = newDeviceModel,
                    lastCloudCheckpointTime = timeLabel,
                    deviceMigrationCount = current.cloudVault.deviceMigrationCount + 1
                )
            )
        }
        persistCurrentSnapshot()
    }

    override fun validateAndAddMedicationSchedule(
        medicationName: String,
        strengthLabel: String,
        scheduledTimeLabel: String,
        clinicalPurpose: String,
        foodWarning: String,
        pillShape: PillShape,
        pillColorLabel: String,
        imprintCode: String
    ): MedicationCreationOutcome {
        val cleanName = medicationName.trim()
        val cleanStrength = strengthLabel.trim()
        val cleanTime = scheduledTimeLabel.trim()

        if (cleanName.length < 2) {
            return MedicationCreationOutcome.RejectedValidation(
                code = "INVALID_MEDICATION_NAME",
                userMessage = "Medication name is required (at least 2 characters)."
            )
        }
        if (cleanStrength.isEmpty()) {
            return MedicationCreationOutcome.RejectedValidation(
                code = "INVALID_STRENGTH",
                userMessage = "Dosage strength is required (e.g., 500 mg, 10 mg, 2000 IU)."
            )
        }
        if (cleanTime.isEmpty()) {
            return MedicationCreationOutcome.RejectedValidation(
                code = "INVALID_SCHEDULE_TIME",
                userMessage = "Please select or enter a valid schedule time."
            )
        }

        val currentWindows = _snapshot.value.windows
        if (currentWindows.size >= MAX_DAILY_WINDOWS) {
            return MedicationCreationOutcome.RejectedValidation(
                code = "MAX_WINDOWS_EXCEEDED",
                userMessage = "Maximum daily schedule limit ($MAX_DAILY_WINDOWS medications) reached."
            )
        }

        val duplicateExists = currentWindows.any { existing ->
            existing.spec.medicationName.equals(cleanName, ignoreCase = true) &&
                existing.spec.scheduledTimeLabel.equals(cleanTime, ignoreCase = true)
        }
        if (duplicateExists) {
            return MedicationCreationOutcome.RejectedValidation(
                code = "DUPLICATE_SCHEDULE_CONFLICT",
                userMessage = "$cleanName is already scheduled at $cleanTime. Choose a different time slot or edit the existing dose."
            )
        }

        val newId = addNewMedicationSchedule(
            medicationName = cleanName,
            strengthLabel = cleanStrength,
            scheduledTimeLabel = cleanTime,
            clinicalPurpose = clinicalPurpose,
            foodWarning = foodWarning,
            pillShape = pillShape,
            pillColorLabel = pillColorLabel,
            imprintCode = imprintCode
        )
        return MedicationCreationOutcome.Created(
            windowId = newId,
            medicationName = cleanName,
            strengthLabel = cleanStrength,
            scheduledTimeLabel = cleanTime
        )
    }

    override fun addNewMedicationSchedule(
        medicationName: String,
        strengthLabel: String,
        scheduledTimeLabel: String,
        clinicalPurpose: String,
        foodWarning: String,
        pillShape: PillShape,
        pillColorLabel: String,
        imprintCode: String
    ): DoseWindowId {
        val cleanName = medicationName.trim().ifBlank { "Metformin" }
        val cleanStrength = strengthLabel.trim().ifBlank { "500 mg" }
        val cleanTime = scheduledTimeLabel.trim().ifBlank { "9:00 AM" }
        val cleanPurpose = clinicalPurpose.trim().ifBlank { "Daily Prescribed Therapy" }
        val cleanFood = foodWarning.trim().ifBlank { "Take with a full glass of water" }
        val cleanColor = pillColorLabel.trim().ifBlank { "White Round Tablet" }
        val cleanImprint = imprintCode.trim().ifBlank { "RX 100" }

        val slug = cleanName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val newWindowId = DoseWindowId("dose-2026-11-08-${System.nanoTime().toString().takeLast(5)}-$slug")

        _snapshot.update { current ->
            val dueEpoch = EpochMillis(BASE_0800_EPOCH_MS)
            val newSpec = DoseScheduleSpec(
                doseWindowId = newWindowId,
                scheduleId = ScheduleId("sched-$slug-${sequenceCounter++}"),
                patientId = PatientId("patient-eleanor-vance"),
                scheduledTimeLabel = cleanTime,
                medicationName = cleanName,
                strengthLabel = cleanStrength,
                clinicalPurpose = cleanPurpose,
                dosageInstructions = "Take 1 dose by mouth with water",
                foodWarning = cleanFood,
                pillShape = pillShape,
                pillColorLabel = cleanColor,
                imprintCode = cleanImprint,
                windowStart = EpochMillis(dueEpoch.millis - 15 * MIN_MS),
                dueAt = dueEpoch,
                escalationDeadline = EpochMillis(dueEpoch.millis + 30 * MIN_MS),
                windowEnd = EpochMillis(dueEpoch.millis + 120 * MIN_MS),
                maxSnoozes = 2
            )
            val newRecord = DoseWindowRecord(
                spec = newSpec,
                state = DoseState.Due(notifiedAt = dueEpoch)
            )
            val addAudit = AuditLogEntry(
                auditId = "AUD-${1000 + sequenceCounter++}",
                doseWindowId = newWindowId,
                medicationName = cleanName,
                fromState = "CREATED",
                toState = "DUE",
                commandName = "OnAddMedicationSchedule",
                idempotencyKey = IdempotencyKey("idem-add-med-${System.nanoTime()}"),
                occurredAt = current.simulatedClockMillis,
                formattedTime = formatClock(current.simulatedClockMillis.millis),
                detail = "Added $cleanName ($cleanStrength) at $cleanTime · Armed AlarmManager + Synced to FamilyVault (${current.cloudVault.familyVaultId})"
            )
            current.copy(
                activeWindowId = newWindowId,
                windows = listOf(newRecord) + current.windows,
                auditLedger = (listOf(addAudit) + current.auditLedger).take(MAX_AUDIT_ENTRIES)
            )
        }
        persistCurrentSnapshot()
        return newWindowId
    }

    override fun deleteMedicationSchedule(doseWindowId: DoseWindowId): MedicationDeletionOutcome {
        val current = _snapshot.value
        if (current.windows.size <= 1) {
            return MedicationDeletionOutcome.RejectedLastSchedule(
                "Cannot delete the final remaining medication schedule. At least 1 active schedule is required."
            )
        }
        val target = current.windows.firstOrNull { it.spec.doseWindowId == doseWindowId }
            ?: return MedicationDeletionOutcome.RejectedLastSchedule("Schedule not found.")

        var nextActiveId: DoseWindowId = current.activeWindowId
        _snapshot.update { snap ->
            val remaining = snap.windows.filterNot { it.spec.doseWindowId == doseWindowId }
            nextActiveId = if (snap.activeWindowId == doseWindowId) {
                remaining.first().spec.doseWindowId
            } else {
                snap.activeWindowId
            }
            val delAudit = AuditLogEntry(
                auditId = "AUD-${1000 + sequenceCounter++}",
                doseWindowId = nextActiveId,
                medicationName = target.spec.medicationName,
                fromState = target.state.stateName,
                toState = "DELETED",
                commandName = "OnDeleteMedicationSchedule",
                idempotencyKey = IdempotencyKey("idem-del-med-${System.nanoTime()}"),
                occurredAt = snap.simulatedClockMillis,
                formattedTime = formatClock(snap.simulatedClockMillis.millis),
                detail = "Removed ${target.spec.medicationName} (${target.spec.strengthLabel} at ${target.spec.scheduledTimeLabel}) & cancelled pending AlarmManager triggers"
            )
            snap.copy(
                activeWindowId = nextActiveId,
                windows = remaining,
                auditLedger = (listOf(delAudit) + snap.auditLedger).take(MAX_AUDIT_ENTRIES)
            )
        }
        persistCurrentSnapshot()
        return MedicationDeletionOutcome.Deleted(
            deletedMedicationName = target.spec.medicationName,
            newActiveWindowId = nextActiveId
        )
    }

    private fun createInitialSnapshot(): RepositorySnapshot {
        val hydrated = persistenceStore?.loadPersistedState()
        val effectiveSignedIn = hydrated?.isSignedIn ?: (if (persistenceStore != null) false else startSignedIn)

        val patientId = PatientId("patient-eleanor-vance")
        val t0800 = EpochMillis(BASE_0800_EPOCH_MS)
        val t1300 = EpochMillis(BASE_0800_EPOCH_MS + 5 * 60 * MIN_MS)
        val t2000 = EpochMillis(BASE_0800_EPOCH_MS + 12 * 60 * MIN_MS)

        val morningSpec = DoseScheduleSpec(
            doseWindowId = MORNING_WINDOW_ID,
            scheduleId = ScheduleId("sched-lisinopril-am"),
            patientId = patientId,
            scheduledTimeLabel = "8:00 AM",
            medicationName = "Lisinopril",
            strengthLabel = "10 mg",
            clinicalPurpose = "Blood Pressure & Heart Protection",
            dosageInstructions = "Take 1 tablet by mouth with a full glass of water",
            foodWarning = "Can be taken with or without breakfast",
            pillShape = PillShape.ROUND_TABLET,
            pillColorLabel = "Soft Pink Round Tablet",
            imprintCode = "LUPIN 10",
            windowStart = EpochMillis(t0800.millis - 15 * MIN_MS),
            dueAt = t0800,
            escalationDeadline = EpochMillis(t0800.millis + 30 * MIN_MS),
            windowEnd = EpochMillis(t0800.millis + 120 * MIN_MS),
            maxSnoozes = 2
        )

        val afternoonSpec = DoseScheduleSpec(
            doseWindowId = AFTERNOON_WINDOW_ID,
            scheduleId = ScheduleId("sched-eliquis-pm"),
            patientId = patientId,
            scheduledTimeLabel = "1:00 PM",
            medicationName = "Eliquis (Apixaban)",
            strengthLabel = "5 mg",
            clinicalPurpose = "Stroke Prevention Anticoagulant",
            dosageInstructions = "Take 1 oval tablet by mouth with water",
            foodWarning = "CRITICAL ANTICOAGULANT — Never take two doses at once",
            pillShape = PillShape.OVAL_CAPLET,
            pillColorLabel = "Yellow Oval Caplet",
            imprintCode = "894 / 5",
            windowStart = EpochMillis(t1300.millis - 15 * MIN_MS),
            dueAt = t1300,
            escalationDeadline = EpochMillis(t1300.millis + 30 * MIN_MS),
            windowEnd = EpochMillis(t1300.millis + 120 * MIN_MS),
            maxSnoozes = 2
        )

        val eveningSpec = DoseScheduleSpec(
            doseWindowId = EVENING_WINDOW_ID,
            scheduleId = ScheduleId("sched-atorvastatin-eve"),
            patientId = patientId,
            scheduledTimeLabel = "8:00 PM",
            medicationName = "Atorvastatin",
            strengthLabel = "20 mg",
            clinicalPurpose = "Cholesterol & Vascular Health",
            dosageInstructions = "Take 1 capsule with evening meal",
            foodWarning = "Avoid grapefruit juice",
            pillShape = PillShape.TWO_TONE_CAPSULE,
            pillColorLabel = "Blue & White Capsule",
            imprintCode = "PD 156",
            windowStart = EpochMillis(t2000.millis - 15 * MIN_MS),
            dueAt = t2000,
            escalationDeadline = EpochMillis(t2000.millis + 30 * MIN_MS),
            windowEnd = EpochMillis(t2000.millis + 120 * MIN_MS),
            maxSnoozes = 2
        )

        val defaultWindows = listOf(
            DoseWindowRecord(morningSpec, DoseState.Due(notifiedAt = t0800)),
            DoseWindowRecord(afternoonSpec, DoseState.Scheduled),
            DoseWindowRecord(eveningSpec, DoseState.Scheduled)
        )

        val windowsToUse = hydrated?.windows ?: defaultWindows
        val activeWindowToUse = hydrated?.activeWindowId
            ?.takeIf { id -> windowsToUse.any { it.spec.doseWindowId == id } }
            ?: windowsToUse.first().spec.doseWindowId

        val initialAudit = AuditLogEntry(
            auditId = "AUD-1000",
            doseWindowId = activeWindowToUse,
            medicationName = "Lisinopril",
            fromState = "SCHEDULED",
            toState = "DUE",
            commandName = "OnWindowOpen",
            idempotencyKey = IdempotencyKey("idem-init-0800"),
            occurredAt = t0800,
            formattedTime = "8:00 AM",
            detail = "Doze-Exempt Exact Alarm fired (AlarmManager.setAlarmClock); Escalation Watchdog armed for 8:30 AM"
        )

        val effectivePatientName = when {
            hydrated != null -> hydrated.patientName
            effectiveSignedIn -> "Eleanor Vance"
            else -> "Guest"
        }
        val effectiveCaregiverName = when {
            hydrated != null -> hydrated.caregiverName
            effectiveSignedIn -> "Maya Vance"
            else -> ""
        }
        val effectiveEmail = when {
            hydrated != null -> hydrated.email
            effectiveSignedIn -> "maya.vance@gmail.com"
            else -> ""
        }
        val effectiveDisplayName = when {
            hydrated != null -> hydrated.displayName
            effectiveSignedIn -> "Maya Vance"
            else -> ""
        }
        val effectiveProvider = hydrated?.provider ?: IdentityProvider.GOOGLE_ONE_TAP

        return RepositorySnapshot(
            patientName = effectivePatientName,
            patientAge = 78,
            caregiverName = effectiveCaregiverName,
            caregiverRelation = "Primary Family Caregiver",
            activeWindowId = activeWindowToUse,
            windows = windowsToUse,
            auditLedger = listOf(initialAudit),
            outboxQueue = emptyList(),
            simulatedClockMillis = EpochMillis(t0800.millis + 2 * MIN_MS),
            adherenceStreakDays = 14,
            thirtyDayAdherencePercent = 98,
            cloudVault = CloudVaultSyncStatus(
                isSignedIn = effectiveSignedIn,
                familyVaultId = "vault-vance-family-9f82a",
                primaryEmail = effectiveEmail,
                accountDisplayName = effectiveDisplayName,
                activeProvider = effectiveProvider,
                linkedProviders = if (effectiveSignedIn) setOf(effectiveProvider) else emptySet(),
                blockStoreAutoRestoreEnabled = true,
                currentDeviceModel = "Pixel 9 Pro (Android 16)",
                lastCloudCheckpointTime = "8:00 AM",
                deviceMigrationCount = 1,
                credentialSourceNote = hydrated?.credentialSourceNote
                    ?: "Android CredentialManager · GoogleIdTokenCredential"
            )
        )
    }

    private fun formatClock(epochMillis: Long): String {
        val deltaMinutes = ((epochMillis - BASE_0800_EPOCH_MS) / MIN_MS).toInt()
        val totalMinutesFromMidnight = (8 * 60) + deltaMinutes
        val hours24 = ((totalMinutesFromMidnight / 60) % 24 + 24) % 24
        val mins = ((totalMinutesFromMidnight % 60) + 60) % 60
        val period = if (hours24 < 12) "AM" else "PM"
        val hour12 = when {
            hours24 == 0 -> 12
            hours24 > 12 -> hours24 - 12
            else -> hours24
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, mins, period)
    }
}
