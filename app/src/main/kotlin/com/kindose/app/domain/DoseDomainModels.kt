package com.kindose.app.domain

/**
 * Branded Value Types ("Parse, Don't Validate" at the boundary).
 * Eliminates primitive obsession across the domain, persistence, and UI layers.
 */
@JvmInline
value class DoseWindowId(val value: String) {
    init {
        require(value.isNotBlank() && value.length <= 128) {
            "DoseWindowId must be non-blank and <= 128 chars"
        }
    }
}

@JvmInline
value class ScheduleId(val value: String) {
    init {
        require(value.isNotBlank() && value.length <= 128) {
            "ScheduleId must be non-blank and <= 128 chars"
        }
    }
}

@JvmInline
value class PatientId(val value: String) {
    init {
        require(value.isNotBlank() && value.length <= 128) {
            "PatientId must be non-blank and <= 128 chars"
        }
    }
}

@JvmInline
value class CaregiverId(val value: String) {
    init {
        require(value.isNotBlank() && value.length <= 128) {
            "CaregiverId must be non-blank and <= 128 chars"
        }
    }
}

@JvmInline
value class IdempotencyKey(val value: String) {
    init {
        require(value.isNotBlank() && value.length <= 128) {
            "IdempotencyKey must be non-blank and <= 128 chars"
        }
    }
}

@JvmInline
value class EpochMillis(val millis: Long) : Comparable<EpochMillis> {
    init {
        require(millis > 0L) { "EpochMillis must be positive (> 0)" }
    }

    override fun compareTo(other: EpochMillis): Int = millis.compareTo(other.millis)
    operator fun plus(durationMs: Long): EpochMillis = EpochMillis(millis + durationMs)
    fun minutesSince(earlier: EpochMillis): Long = (millis - earlier.millis).coerceAtLeast(0L) / 60_000L
    fun minutesUntil(later: EpochMillis): Long = (later.millis - millis).coerceAtLeast(0L) / 60_000L
}

/**
 * Visual & tactile pill identification metadata for non-color-alone senior recognition (WCAG 1.4.1).
 */
enum class PillShape(val displayName: String, val a11yDescription: String) {
    ROUND_TABLET("Round Tablet", "small round tablet scored in the middle"),
    OVAL_CAPLET("Oval Caplet", "smooth oval-shaped caplet"),
    TWO_TONE_CAPSULE("Two-Tone Capsule", "two-tone oblong gelatin capsule")
}

/**
 * Immutable time boundaries and clinical metadata for a single scheduled medication dose window.
 */
data class DoseScheduleSpec(
    val doseWindowId: DoseWindowId,
    val scheduleId: ScheduleId,
    val patientId: PatientId,
    val scheduledTimeLabel: String,
    val medicationName: String,
    val strengthLabel: String,
    val clinicalPurpose: String,
    val dosageInstructions: String,
    val foodWarning: String,
    val pillShape: PillShape,
    val pillColorLabel: String,
    val imprintCode: String,
    val windowStart: EpochMillis,
    val dueAt: EpochMillis,
    val escalationDeadline: EpochMillis,
    val windowEnd: EpochMillis,
    val maxSnoozes: Int = 2,
    val snoozeDurationMs: Long = 10L * 60_000L
) {
    init {
        require(medicationName.isNotBlank()) { "Medication name cannot be blank" }
        require(dosageInstructions.isNotBlank()) { "Dosage instructions cannot be blank" }
        require(windowStart <= dueAt) { "windowStart must be <= dueAt" }
        require(dueAt < escalationDeadline) { "dueAt must be strictly before escalationDeadline" }
        require(escalationDeadline < windowEnd) { "escalationDeadline must be strictly before windowEnd" }
        require(maxSnoozes in 0..5) { "maxSnoozes must be bounded between 0 and 5" }
    }
}

enum class ConfirmationSource(val label: String) {
    SENIOR_APPLIANCE_TAP("Confirmed by Senior on Appliance Screen"),
    SENIOR_LATE_TAP_AFTER_ESCALATION("Confirmed Late by Senior (All-Clear Sent)")
}

/**
 * Explicit Finite State Machine states for a DoseWindow.
 * Makes illegal states unrepresentable at compile time.
 */
sealed interface DoseState {
    val stateName: String

    data object Scheduled : DoseState {
        override val stateName: String = "SCHEDULED"
    }

    data class Due(
        val notifiedAt: EpochMillis
    ) : DoseState {
        override val stateName: String = "DUE"
    }

    data class Snoozed(
        val snoozeCount: Int,
        val snoozeUntil: EpochMillis
    ) : DoseState {
        init {
            require(snoozeCount >= 1) { "Snoozed state requires snoozeCount >= 1" }
        }
        override val stateName: String = "SNOOZED"
    }

    data class Confirmed(
        val confirmedAt: EpochMillis,
        val idempotencyKey: IdempotencyKey,
        val source: ConfirmationSource
    ) : DoseState {
        override val stateName: String = "CONFIRMED"
    }

    data class Escalated(
        val escalatedAt: EpochMillis,
        val snoozeCountPrior: Int
    ) : DoseState {
        override val stateName: String = "ESCALATED"
    }

    data class ResolvedByCaregiver(
        val resolvedAt: EpochMillis,
        val caregiverId: CaregiverId,
        val caregiverName: String,
        val resolutionNote: String,
        val idempotencyKey: IdempotencyKey
    ) : DoseState {
        init {
            require(resolutionNote.isNotBlank()) { "Caregiver resolution note is required for auditability" }
        }
        override val stateName: String = "RESOLVED_BY_CAREGIVER"
    }

    data class MissedExpired(
        val expiredAt: EpochMillis
    ) : DoseState {
        override val stateName: String = "MISSED_EXPIRED"
    }
}

/**
 * Commands submitted to the pure functional core.
 */
sealed interface DoseCommand {
    val occurredAt: EpochMillis
    val idempotencyKey: IdempotencyKey

    data class OnWindowOpen(
        override val occurredAt: EpochMillis,
        override val idempotencyKey: IdempotencyKey
    ) : DoseCommand

    data class OnSeniorConfirm(
        override val occurredAt: EpochMillis,
        override val idempotencyKey: IdempotencyKey
    ) : DoseCommand

    data class OnSeniorSnooze(
        override val occurredAt: EpochMillis,
        override val idempotencyKey: IdempotencyKey
    ) : DoseCommand

    data class OnSnoozeWakeup(
        override val occurredAt: EpochMillis,
        override val idempotencyKey: IdempotencyKey
    ) : DoseCommand

    data class OnGraceDeadlineExpired(
        override val occurredAt: EpochMillis,
        override val idempotencyKey: IdempotencyKey
    ) : DoseCommand

    data class OnCaregiverResolve(
        val caregiverId: CaregiverId,
        val caregiverName: String,
        val resolutionNote: String,
        override val occurredAt: EpochMillis,
        override val idempotencyKey: IdempotencyKey
    ) : DoseCommand

    data class OnWindowClosed(
        override val occurredAt: EpochMillis,
        override val idempotencyKey: IdempotencyKey
    ) : DoseCommand
}

/**
 * Side effects emitted by the pure state machine to be committed in an atomic SQLite WAL transaction.
 */
sealed interface DoseSideEffect {
    data class ScheduleExactWakeupAlarm(
        val doseWindowId: DoseWindowId,
        val triggerAt: EpochMillis,
        val reason: String
    ) : DoseSideEffect

    data class CancelPendingAlarms(
        val doseWindowId: DoseWindowId
    ) : DoseSideEffect

    data class EnqueueTransactionalOutboxEvent(
        val doseWindowId: DoseWindowId,
        val idempotencyKey: IdempotencyKey,
        val eventType: OutboxEventType,
        val occurredAt: EpochMillis,
        val summary: String
    ) : DoseSideEffect

    data class TriggerDoubleDosePreventionShield(
        val doseWindowId: DoseWindowId,
        val originallyTakenAt: EpochMillis,
        val minutesSinceTaken: Long
    ) : DoseSideEffect
}

enum class OutboxEventType(val displayLabel: String) {
    DOSE_CONFIRMED_ON_TIME("Senior Confirmed Dose On-Time"),
    DOSE_ESCALATED_TO_CAREGIVER("Grace Period Expired -> Caregiver Push/SMS Dispatched"),
    CAREGIVER_ALL_CLEAR_LATE_CONFIRM("Senior Confirmed Late -> Caregiver All-Clear Sent"),
    CAREGIVER_MANUALLY_RESOLVED("Caregiver Verified & Resolved Dose"),
    DOSE_WINDOW_MISSED_EXPIRED("Dose Window Closed Unconfirmed")
}

/**
 * Immutable Audit Log Entry & Outbox Record for compliance and Caregiver transparency.
 */
data class AuditLogEntry(
    val auditId: String,
    val doseWindowId: DoseWindowId,
    val medicationName: String,
    val fromState: String,
    val toState: String,
    val commandName: String,
    val idempotencyKey: IdempotencyKey,
    val occurredAt: EpochMillis,
    val formattedTime: String,
    val detail: String
)

data class TransactionalOutboxRecord(
    val outboxId: String,
    val idempotencyKey: IdempotencyKey,
    val eventType: OutboxEventType,
    val medicationName: String,
    val deliveryStatus: String,
    val formattedTime: String
)

/**
 * Multi-Provider Identity & Zero-Loss Device Migration Models
 * Backed by Android Jetpack CredentialManager + Google Play Block Store + Cloud Vault.
 */
enum class IdentityProvider(
    val displayName: String,
    val protocolBadge: String,
    val description: String
) {
    GOOGLE_ONE_TAP(
        displayName = "Google Account",
        protocolBadge = "Jetpack CredentialManager",
        description = "Native 1-tap bottom sheet via GetGoogleIdOption"
    ),
    PASSKEY_FIDO2(
        displayName = "Biometric Passkey",
        protocolBadge = "FIDO2 / WebAuthn",
        description = "Hardware-backed passkey synced via Google Password Manager"
    ),
    FACEBOOK_OAUTH(
        displayName = "Facebook Login",
        protocolBadge = "OIDC / OAuth 2.1",
        description = "Federated social identity linked to canonical FamilyVaultId"
    ),
    SENIOR_QR_PAIRING(
        displayName = "Senior Zero-Login QR",
        protocolBadge = "Ed25519 Hardware Key",
        description = "Caregiver scans 1-time QR on Mom's new phone — zero typing"
    )
}

data class CloudVaultSyncStatus(
    val familyVaultId: String,
    val primaryEmail: String,
    val activeProvider: IdentityProvider,
    val linkedProviders: Set<IdentityProvider>,
    val blockStoreAutoRestoreEnabled: Boolean,
    val currentDeviceModel: String,
    val lastCloudCheckpointTime: String,
    val deviceMigrationCount: Int
)

