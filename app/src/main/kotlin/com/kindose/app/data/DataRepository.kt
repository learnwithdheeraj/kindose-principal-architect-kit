package com.kindose.app.data

import com.kindose.app.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
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

interface DataRepository {
    val snapshot: StateFlow<RepositorySnapshot>
    fun selectDoseWindow(doseWindowId: DoseWindowId)
    fun dispatchCommand(doseWindowId: DoseWindowId, command: DoseCommand): TransitionOutcome
    fun simulateDuplicateTapAfter18Minutes(doseWindowId: DoseWindowId)
    fun simulateFastForwardToEscalation(doseWindowId: DoseWindowId)
    fun resetWindowToDue(doseWindowId: DoseWindowId)
    fun switchIdentityProvider(provider: IdentityProvider)
    fun simulateDeviceChangeRestore(newDeviceModel: String, viaProvider: IdentityProvider)
}

/**
 * Hermetic, Thread-Safe Transactional Repository implementing the RFC-001 Outbox + Audit Ledger contract.
 * Updates `windows`, `auditLedger`, and `outboxQueue` atomically inside a single `MutableStateFlow.update` block.
 */
class DefaultDataRepository : DataRepository {

    companion object {
        // Base anchor: 08:02 AM local simulation epoch
        const val BASE_0800_EPOCH_MS = 1_762_416_000_000L
        private const val MIN_MS = 60_000L

        val MORNING_WINDOW_ID = DoseWindowId("dose-2026-11-06-0800-lisinopril")
        val AFTERNOON_WINDOW_ID = DoseWindowId("dose-2026-11-06-1300-eliquis")
        val EVENING_WINDOW_ID = DoseWindowId("dose-2026-11-06-2000-atorvastatin")
    }

    private val processedIdempotencyKeys = mutableSetOf<String>()
    private var sequenceCounter = 1

    private val _snapshot = MutableStateFlow(createInitialSnapshot())
    override val snapshot: StateFlow<RepositorySnapshot> = _snapshot.asStateFlow()

    override fun selectDoseWindow(doseWindowId: DoseWindowId) {
        _snapshot.update { current ->
            current.copy(activeWindowId = doseWindowId)
        }
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
                        detail = if (shieldEffect != null) {
                            "DOUBLE-DOSE SHIELD BLOCKED DUPLICATE TAP (+${shieldEffect.minutesSinceTaken}m after original confirmation)"
                        } else {
                            "Transitioned ${outcome.previousState.stateName} -> ${outcome.newState.stateName}"
                        }
                    )

                    val newOutboxItems = outboxEffects.map { effect ->
                        TransactionalOutboxRecord(
                            outboxId = "OUT-${2000 + sequenceCounter++}",
                            idempotencyKey = effect.idempotencyKey,
                            eventType = effect.eventType,
                            medicationName = record.spec.medicationName,
                            deliveryStatus = "SYNCED (WAL -> TLS 1.3)",
                            formattedTime = timeLabel
                        )
                    }

                    current.copy(
                        windows = updatedWindows,
                        auditLedger = listOf(newAudit) + current.auditLedger,
                        outboxQueue = newOutboxItems + current.outboxQueue,
                        simulatedClockMillis = command.occurredAt
                    )
                }

                is TransitionOutcome.RejectedDomainRule -> current
            }
        }

        return resultOutcome ?: TransitionOutcome.RejectedDomainRule(
            DoseState.Scheduled,
            RejectionCode.INVALID_STATE_TRANSITION,
            "Window not found"
        )
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
        val baseTime = ( _snapshot.value.windows.first { it.spec.doseWindowId == doseWindowId }.state as? DoseState.Confirmed )
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
    }

    override fun switchIdentityProvider(provider: IdentityProvider) {
        _snapshot.update { current ->
            val updatedLinked = current.cloudVault.linkedProviders + provider
            current.copy(
                cloudVault = current.cloudVault.copy(
                    activeProvider = provider,
                    linkedProviders = updatedLinked
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
                auditLedger = listOf(migrationAudit) + current.auditLedger,
                cloudVault = current.cloudVault.copy(
                    activeProvider = viaProvider,
                    linkedProviders = current.cloudVault.linkedProviders + viaProvider,
                    currentDeviceModel = newDeviceModel,
                    lastCloudCheckpointTime = timeLabel,
                    deviceMigrationCount = current.cloudVault.deviceMigrationCount + 1
                )
            )
        }
    }

    private fun createInitialSnapshot(): RepositorySnapshot {
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

        val initialAudit = AuditLogEntry(
            auditId = "AUD-1000",
            doseWindowId = MORNING_WINDOW_ID,
            medicationName = "Lisinopril",
            fromState = "SCHEDULED",
            toState = "DUE",
            commandName = "OnWindowOpen",
            idempotencyKey = IdempotencyKey("idem-init-0800"),
            occurredAt = t0800,
            formattedTime = "8:00 AM",
            detail = "Doze-Exempt Exact Alarm fired (AlarmManager.setAlarmClock); Escalation Watchdog armed for 8:30 AM"
        )

        return RepositorySnapshot(
            patientName = "Eleanor Vance",
            patientAge = 78,
            caregiverName = "Maya Vance",
            caregiverRelation = "Daughter (Primary Caregiver)",
            activeWindowId = MORNING_WINDOW_ID,
            windows = listOf(
                DoseWindowRecord(morningSpec, DoseState.Due(notifiedAt = t0800)),
                DoseWindowRecord(afternoonSpec, DoseState.Scheduled),
                DoseWindowRecord(eveningSpec, DoseState.Scheduled)
            ),
            auditLedger = listOf(initialAudit),
            outboxQueue = emptyList(),
            simulatedClockMillis = EpochMillis(t0800.millis + 2 * MIN_MS),
            adherenceStreakDays = 14,
            thirtyDayAdherencePercent = 98,
            cloudVault = CloudVaultSyncStatus(
                familyVaultId = "vault-vance-family-9f82a",
                primaryEmail = "maya.vance@gmail.com",
                activeProvider = IdentityProvider.GOOGLE_ONE_TAP,
                linkedProviders = setOf(
                    IdentityProvider.GOOGLE_ONE_TAP,
                    IdentityProvider.PASSKEY_FIDO2,
                    IdentityProvider.FACEBOOK_OAUTH,
                    IdentityProvider.SENIOR_QR_PAIRING
                ),
                blockStoreAutoRestoreEnabled = true,
                currentDeviceModel = "Pixel 9 Pro (Android 16)",
                lastCloudCheckpointTime = "8:00 AM",
                deviceMigrationCount = 1
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
