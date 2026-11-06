package com.kindose.app.domain

sealed interface TransitionOutcome {
    data class Applied(
        val previousState: DoseState,
        val newState: DoseState,
        val sideEffects: List<DoseSideEffect>
    ) : TransitionOutcome

    data class RejectedDomainRule(
        val currentState: DoseState,
        val code: RejectionCode,
        val reason: String
    ) : TransitionOutcome
}

enum class RejectionCode {
    WINDOW_NOT_OPEN_YET,
    SNOOZE_LIMIT_REACHED,
    PAST_ESCALATION_DEADLINE,
    INVALID_STATE_TRANSITION,
    DEADLINE_NOT_REACHED_YET
}

/**
 * Pure, Deterministic Functional Core for KinDose Medication Safety.
 * Zero I/O, zero wall-clock calls, 100% deterministic.
 */
object DoseEscalationStateMachine {

    fun evaluate(
        spec: DoseScheduleSpec,
        currentState: DoseState,
        command: DoseCommand
    ): TransitionOutcome = when (command) {
        is DoseCommand.OnWindowOpen -> handleWindowOpen(spec, currentState, command)
        is DoseCommand.OnSeniorConfirm -> handleSeniorConfirm(spec, currentState, command)
        is DoseCommand.OnSeniorSnooze -> handleSeniorSnooze(spec, currentState, command)
        is DoseCommand.OnSnoozeWakeup -> handleSnoozeWakeup(spec, currentState, command)
        is DoseCommand.OnGraceDeadlineExpired -> handleGraceExpired(spec, currentState, command)
        is DoseCommand.OnCaregiverResolve -> handleCaregiverResolve(spec, currentState, command)
        is DoseCommand.OnWindowClosed -> handleWindowClosed(spec, currentState, command)
    }

    private fun handleWindowOpen(
        spec: DoseScheduleSpec,
        state: DoseState,
        cmd: DoseCommand.OnWindowOpen
    ): TransitionOutcome {
        if (cmd.occurredAt < spec.windowStart) {
            return TransitionOutcome.RejectedDomainRule(
                state,
                RejectionCode.WINDOW_NOT_OPEN_YET,
                "Cannot open window before windowStart"
            )
        }
        return when (state) {
            is DoseState.Scheduled -> TransitionOutcome.Applied(
                previousState = state,
                newState = DoseState.Due(notifiedAt = cmd.occurredAt),
                sideEffects = listOf(
                    DoseSideEffect.ScheduleExactWakeupAlarm(
                        doseWindowId = spec.doseWindowId,
                        triggerAt = spec.escalationDeadline,
                        reason = "ESCALATION_DEADLINE_WATCHDOG"
                    )
                )
            )
            is DoseState.Due -> TransitionOutcome.Applied(state, state, emptyList())
            else -> TransitionOutcome.RejectedDomainRule(
                state,
                RejectionCode.INVALID_STATE_TRANSITION,
                "Cannot open window from ${state.stateName}"
            )
        }
    }

    private fun handleSeniorConfirm(
        spec: DoseScheduleSpec,
        state: DoseState,
        cmd: DoseCommand.OnSeniorConfirm
    ): TransitionOutcome {
        if (cmd.occurredAt < spec.windowStart) {
            return TransitionOutcome.RejectedDomainRule(
                state,
                RejectionCode.WINDOW_NOT_OPEN_YET,
                "Cannot confirm dose before windowStart"
            )
        }

        return when (state) {
            is DoseState.Confirmed -> {
                val minutesElapsed = cmd.occurredAt.minutesSince(state.confirmedAt)
                TransitionOutcome.Applied(
                    previousState = state,
                    newState = state, // Original confirmedAt is strictly preserved!
                    sideEffects = listOf(
                        DoseSideEffect.TriggerDoubleDosePreventionShield(
                            doseWindowId = spec.doseWindowId,
                            originallyTakenAt = state.confirmedAt,
                            minutesSinceTaken = minutesElapsed
                        )
                    )
                )
            }

            is DoseState.Scheduled,
            is DoseState.Due,
            is DoseState.Snoozed -> {
                val newState = DoseState.Confirmed(
                    confirmedAt = cmd.occurredAt,
                    idempotencyKey = cmd.idempotencyKey,
                    source = ConfirmationSource.SENIOR_APPLIANCE_TAP
                )
                TransitionOutcome.Applied(
                    previousState = state,
                    newState = newState,
                    sideEffects = listOf(
                        DoseSideEffect.CancelPendingAlarms(spec.doseWindowId),
                        DoseSideEffect.EnqueueTransactionalOutboxEvent(
                            doseWindowId = spec.doseWindowId,
                            idempotencyKey = cmd.idempotencyKey,
                            eventType = OutboxEventType.DOSE_CONFIRMED_ON_TIME,
                            occurredAt = cmd.occurredAt,
                            summary = "${spec.medicationName} (${spec.strengthLabel}) confirmed on time"
                        )
                    )
                )
            }

            is DoseState.Escalated -> {
                val newState = DoseState.Confirmed(
                    confirmedAt = cmd.occurredAt,
                    idempotencyKey = cmd.idempotencyKey,
                    source = ConfirmationSource.SENIOR_LATE_TAP_AFTER_ESCALATION
                )
                TransitionOutcome.Applied(
                    previousState = state,
                    newState = newState,
                    sideEffects = listOf(
                        DoseSideEffect.CancelPendingAlarms(spec.doseWindowId),
                        DoseSideEffect.EnqueueTransactionalOutboxEvent(
                            doseWindowId = spec.doseWindowId,
                            idempotencyKey = cmd.idempotencyKey,
                            eventType = OutboxEventType.CAREGIVER_ALL_CLEAR_LATE_CONFIRM,
                            occurredAt = cmd.occurredAt,
                            summary = "${spec.medicationName} confirmed late by senior — All-Clear sent to family"
                        )
                    )
                )
            }

            is DoseState.ResolvedByCaregiver,
            is DoseState.MissedExpired -> TransitionOutcome.RejectedDomainRule(
                state,
                RejectionCode.INVALID_STATE_TRANSITION,
                "Dose window already closed in terminal state ${state.stateName}"
            )
        }
    }

    private fun handleSeniorSnooze(
        spec: DoseScheduleSpec,
        state: DoseState,
        cmd: DoseCommand.OnSeniorSnooze
    ): TransitionOutcome {
        if (cmd.occurredAt >= spec.escalationDeadline) {
            return TransitionOutcome.RejectedDomainRule(
                state,
                RejectionCode.PAST_ESCALATION_DEADLINE,
                "Cannot snooze at or past caregiver escalation deadline"
            )
        }

        val currentSnoozeCount = when (state) {
            is DoseState.Due -> 0
            is DoseState.Snoozed -> state.snoozeCount
            else -> return TransitionOutcome.RejectedDomainRule(
                state,
                RejectionCode.INVALID_STATE_TRANSITION,
                "Can only snooze from DUE or SNOOZED state"
            )
        }

        if (currentSnoozeCount >= spec.maxSnoozes) {
            return TransitionOutcome.RejectedDomainRule(
                state,
                RejectionCode.SNOOZE_LIMIT_REACHED,
                "Maximum snooze count (${spec.maxSnoozes}) reached"
            )
        }

        val unclampedWakeup = cmd.occurredAt + spec.snoozeDurationMs
        val clampedWakeup = if (unclampedWakeup < spec.escalationDeadline) {
            unclampedWakeup
        } else {
            spec.escalationDeadline
        }

        val newState = DoseState.Snoozed(
            snoozeCount = currentSnoozeCount + 1,
            snoozeUntil = clampedWakeup
        )

        return TransitionOutcome.Applied(
            previousState = state,
            newState = newState,
            sideEffects = listOf(
                DoseSideEffect.ScheduleExactWakeupAlarm(
                    doseWindowId = spec.doseWindowId,
                    triggerAt = clampedWakeup,
                    reason = "SNOOZE_WAKEUP_${newState.snoozeCount}"
                )
            )
        )
    }

    private fun handleSnoozeWakeup(
        spec: DoseScheduleSpec,
        state: DoseState,
        cmd: DoseCommand.OnSnoozeWakeup
    ): TransitionOutcome = when (state) {
        is DoseState.Snoozed -> {
            if (cmd.occurredAt >= spec.escalationDeadline) {
                escalateToCaregiver(spec, state, state.snoozeCount, cmd.occurredAt, cmd.idempotencyKey)
            } else {
                TransitionOutcome.Applied(
                    previousState = state,
                    newState = DoseState.Due(notifiedAt = cmd.occurredAt),
                    sideEffects = listOf(
                        DoseSideEffect.ScheduleExactWakeupAlarm(
                            doseWindowId = spec.doseWindowId,
                            triggerAt = spec.escalationDeadline,
                            reason = "ESCALATION_DEADLINE_WATCHDOG"
                        )
                    )
                )
            }
        }
        else -> TransitionOutcome.RejectedDomainRule(
            state,
            RejectionCode.INVALID_STATE_TRANSITION,
            "OnSnoozeWakeup only valid from SNOOZED state"
        )
    }

    private fun handleGraceExpired(
        spec: DoseScheduleSpec,
        state: DoseState,
        cmd: DoseCommand.OnGraceDeadlineExpired
    ): TransitionOutcome {
        if (cmd.occurredAt < spec.escalationDeadline) {
            return TransitionOutcome.RejectedDomainRule(
                state,
                RejectionCode.DEADLINE_NOT_REACHED_YET,
                "Cannot escalate before escalationDeadline"
            )
        }

        return when (state) {
            is DoseState.Scheduled -> escalateToCaregiver(spec, state, 0, cmd.occurredAt, cmd.idempotencyKey)
            is DoseState.Due -> escalateToCaregiver(spec, state, 0, cmd.occurredAt, cmd.idempotencyKey)
            is DoseState.Snoozed -> escalateToCaregiver(spec, state, state.snoozeCount, cmd.occurredAt, cmd.idempotencyKey)
            is DoseState.Escalated,
            is DoseState.Confirmed,
            is DoseState.ResolvedByCaregiver,
            is DoseState.MissedExpired -> TransitionOutcome.Applied(state, state, emptyList())
        }
    }

    private fun escalateToCaregiver(
        spec: DoseScheduleSpec,
        previousState: DoseState,
        snoozeCount: Int,
        occurredAt: EpochMillis,
        idempotencyKey: IdempotencyKey
    ): TransitionOutcome {
        val newState = DoseState.Escalated(
            escalatedAt = occurredAt,
            snoozeCountPrior = snoozeCount
        )
        return TransitionOutcome.Applied(
            previousState = previousState,
            newState = newState,
            sideEffects = listOf(
                DoseSideEffect.EnqueueTransactionalOutboxEvent(
                    doseWindowId = spec.doseWindowId,
                    idempotencyKey = idempotencyKey,
                    eventType = OutboxEventType.DOSE_ESCALATED_TO_CAREGIVER,
                    occurredAt = occurredAt,
                    summary = "Grace deadline breached for ${spec.medicationName} — Caregiver alerted"
                ),
                DoseSideEffect.ScheduleExactWakeupAlarm(
                    doseWindowId = spec.doseWindowId,
                    triggerAt = spec.windowEnd,
                    reason = "WINDOW_EXPIRATION_WATCHDOG"
                )
            )
        )
    }

    private fun handleCaregiverResolve(
        spec: DoseScheduleSpec,
        state: DoseState,
        cmd: DoseCommand.OnCaregiverResolve
    ): TransitionOutcome = when (state) {
        is DoseState.Escalated,
        is DoseState.Due,
        is DoseState.Snoozed -> {
            val newState = DoseState.ResolvedByCaregiver(
                resolvedAt = cmd.occurredAt,
                caregiverId = cmd.caregiverId,
                caregiverName = cmd.caregiverName,
                resolutionNote = cmd.resolutionNote,
                idempotencyKey = cmd.idempotencyKey
            )
            TransitionOutcome.Applied(
                previousState = state,
                newState = newState,
                sideEffects = listOf(
                    DoseSideEffect.CancelPendingAlarms(spec.doseWindowId),
                    DoseSideEffect.EnqueueTransactionalOutboxEvent(
                        doseWindowId = spec.doseWindowId,
                        idempotencyKey = cmd.idempotencyKey,
                        eventType = OutboxEventType.CAREGIVER_MANUALLY_RESOLVED,
                        occurredAt = cmd.occurredAt,
                        summary = "${cmd.caregiverName} verified dose by phone: \"${cmd.resolutionNote}\""
                    )
                )
            )
        }
        is DoseState.ResolvedByCaregiver -> TransitionOutcome.Applied(state, state, emptyList())
        else -> TransitionOutcome.RejectedDomainRule(
            state,
            RejectionCode.INVALID_STATE_TRANSITION,
            "Caregiver cannot resolve dose in state ${state.stateName}"
        )
    }

    private fun handleWindowClosed(
        spec: DoseScheduleSpec,
        state: DoseState,
        cmd: DoseCommand.OnWindowClosed
    ): TransitionOutcome {
        if (cmd.occurredAt < spec.windowEnd) {
            return TransitionOutcome.RejectedDomainRule(
                state,
                RejectionCode.DEADLINE_NOT_REACHED_YET,
                "Window has not reached windowEnd yet"
            )
        }
        return when (state) {
            is DoseState.Confirmed,
            is DoseState.ResolvedByCaregiver,
            is DoseState.MissedExpired -> TransitionOutcome.Applied(state, state, emptyList())
            is DoseState.Scheduled,
            is DoseState.Due,
            is DoseState.Snoozed,
            is DoseState.Escalated -> {
                val newState = DoseState.MissedExpired(expiredAt = cmd.occurredAt)
                TransitionOutcome.Applied(
                    previousState = state,
                    newState = newState,
                    sideEffects = listOf(
                        DoseSideEffect.CancelPendingAlarms(spec.doseWindowId),
                        DoseSideEffect.EnqueueTransactionalOutboxEvent(
                            doseWindowId = spec.doseWindowId,
                            idempotencyKey = cmd.idempotencyKey,
                            eventType = OutboxEventType.DOSE_WINDOW_MISSED_EXPIRED,
                            occurredAt = cmd.occurredAt,
                            summary = "Window closed for ${spec.medicationName}"
                        )
                    )
                )
            }
        }
    }
}
