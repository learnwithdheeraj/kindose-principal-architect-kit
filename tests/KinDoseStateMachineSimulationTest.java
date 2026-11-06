import java.util.*;

/**
 * Gold-Standard Deterministic Simulation & Property-Based Invariant Verification Suite
 * for the KinDose Medication Safety & Escalation State Machine.
 *
 * Verifies:
 * 1. Happy Path: Window Open -> Senior Confirm -> Outbox Event + Alarm Cancellation.
 * 2. SAFETY INVARIANT 1 (Double-Dose Lockout Shield): Duplicate taps after confirmation NEVER overwrite
 *    the original timestamp or emit duplicate confirmation events, and ALWAYS emit TriggerDoubleDosePreventionShield.
 * 3. SAFETY INVARIANT 2 (Bounded Snooze & Escalation Clamping): Snoozes are capped at maxSnoozes (2),
 *    and snoozeUntil can NEVER exceed escalationDeadline.
 * 4. SAFETY INVARIANT 3 (Late Confirm All-Clear): Confirming after ESCALATED transitions to CONFIRMED
 *    and emits CAREGIVER_ALL_CLEAR_LATE_CONFIRM.
 * 5. SAFETY INVARIANT 4 (Deterministic Fuzzing / Property Testing): Across 10,000 randomized command
 *    sequences with a fixed PRNG seed, no sequence ever violates state machine invariants.
 */
public class KinDoseStateMachineSimulationTest {

    enum StateType {
        SCHEDULED, DUE, SNOOZED, CONFIRMED, ESCALATED, RESOLVED_BY_CAREGIVER, MISSED_EXPIRED
    }

    enum EffectType {
        SCHEDULE_EXACT_ALARM,
        CANCEL_ALARMS,
        OUTBOX_CONFIRMED_ON_TIME,
        OUTBOX_ESCALATED_TO_CAREGIVER,
        OUTBOX_CAREGIVER_ALL_CLEAR,
        OUTBOX_CAREGIVER_RESOLVED,
        OUTBOX_MISSED_EXPIRED,
        TRIGGER_DOUBLE_DOSE_SHIELD
    }

    record Spec(
        String doseWindowId,
        long windowStart,
        long dueAt,
        long escalationDeadline,
        long windowEnd,
        int maxSnoozes,
        long snoozeDurationMs
    ) {
        Spec {
            if (windowStart > dueAt || dueAt >= escalationDeadline || escalationDeadline >= windowEnd) {
                throw new IllegalArgumentException("Invalid chronological bounds");
            }
        }
    }

    record State(
        StateType type,
        int snoozeCount,
        Long snoozeUntil,
        Long confirmedAt,
        String idempotencyKey
    ) {
        static State scheduled() {
            return new State(StateType.SCHEDULED, 0, null, null, null);
        }
    }

    record SideEffect(EffectType type, long timestamp, String detail) {}

    record Outcome(boolean applied, State nextState, List<SideEffect> effects, String rejectionReason) {}

    static class StateMachine {
        static Outcome evaluateWindowOpen(Spec spec, State state, long now) {
            if (now < spec.windowStart()) {
                return new Outcome(false, state, List.of(), "WINDOW_NOT_OPEN_YET");
            }
            if (state.type() == StateType.SCHEDULED) {
                return new Outcome(
                    true,
                    new State(StateType.DUE, 0, null, null, null),
                    List.of(new SideEffect(EffectType.SCHEDULE_EXACT_ALARM, spec.escalationDeadline(), "ESCALATION_WATCHDOG")),
                    null
                );
            }
            if (state.type() == StateType.DUE) {
                return new Outcome(true, state, List.of(), null);
            }
            return new Outcome(false, state, List.of(), "INVALID_STATE_TRANSITION");
        }

        static Outcome evaluateSeniorConfirm(Spec spec, State state, long now, String idemKey) {
            if (now < spec.windowStart()) {
                return new Outcome(false, state, List.of(), "WINDOW_NOT_OPEN_YET");
            }
            return switch (state.type()) {
                case CONFIRMED -> {
                    long minutesElapsed = Math.max(0L, (now - state.confirmedAt()) / 60_000L);
                    yield new Outcome(
                        true,
                        state, // Immutable original confirmedAt preserved!
                        List.of(new SideEffect(EffectType.TRIGGER_DOUBLE_DOSE_SHIELD, state.confirmedAt(), "mins=" + minutesElapsed)),
                        null
                    );
                }
                case SCHEDULED, DUE, SNOOZED -> new Outcome(
                    true,
                    new State(StateType.CONFIRMED, state.snoozeCount(), null, now, idemKey),
                    List.of(
                        new SideEffect(EffectType.CANCEL_ALARMS, now, spec.doseWindowId()),
                        new SideEffect(EffectType.OUTBOX_CONFIRMED_ON_TIME, now, idemKey)
                    ),
                    null
                );
                case ESCALATED -> new Outcome(
                    true,
                    new State(StateType.CONFIRMED, state.snoozeCount(), null, now, idemKey),
                    List.of(
                        new SideEffect(EffectType.CANCEL_ALARMS, now, spec.doseWindowId()),
                        new SideEffect(EffectType.OUTBOX_CAREGIVER_ALL_CLEAR, now, idemKey)
                    ),
                    null
                );
                case RESOLVED_BY_CAREGIVER, MISSED_EXPIRED ->
                    new Outcome(false, state, List.of(), "TERMINAL_STATE_LOCKED");
            };
        }

        static Outcome evaluateSeniorSnooze(Spec spec, State state, long now) {
            if (now >= spec.escalationDeadline()) {
                return new Outcome(false, state, List.of(), "PAST_ESCALATION_DEADLINE");
            }
            if (state.type() != StateType.DUE && state.type() != StateType.SNOOZED) {
                return new Outcome(false, state, List.of(), "INVALID_STATE_TRANSITION");
            }
            if (state.snoozeCount() >= spec.maxSnoozes()) {
                return new Outcome(false, state, List.of(), "SNOOZE_LIMIT_REACHED");
            }
            long clampedWakeup = Math.min(now + spec.snoozeDurationMs(), spec.escalationDeadline());
            int nextCount = state.snoozeCount() + 1;
            return new Outcome(
                true,
                new State(StateType.SNOOZED, nextCount, clampedWakeup, null, null),
                List.of(new SideEffect(EffectType.SCHEDULE_EXACT_ALARM, clampedWakeup, "SNOOZE_" + nextCount)),
                null
            );
        }

        static Outcome evaluateGraceExpired(Spec spec, State state, long now, String idemKey) {
            if (now < spec.escalationDeadline()) {
                return new Outcome(false, state, List.of(), "DEADLINE_NOT_REACHED_YET");
            }
            return switch (state.type()) {
                case SCHEDULED, DUE, SNOOZED -> new Outcome(
                    true,
                    new State(StateType.ESCALATED, state.snoozeCount(), null, null, idemKey),
                    List.of(
                        new SideEffect(EffectType.OUTBOX_ESCALATED_TO_CAREGIVER, now, idemKey),
                        new SideEffect(EffectType.SCHEDULE_EXACT_ALARM, spec.windowEnd(), "WINDOW_EXPIRATION")
                    ),
                    null
                );
                case ESCALATED, CONFIRMED, RESOLVED_BY_CAREGIVER, MISSED_EXPIRED ->
                    new Outcome(true, state, List.of(), null);
            };
        }
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("INVARIANT VIOLATION: " + message);
        }
    }

    public static void main(String[] args) {
        long t0800 = 1_700_000_000_000L;
        long t0745 = t0800 - 15 * 60_000L; // Window start 07:45
        long t0830 = t0800 + 30 * 60_000L; // Escalation deadline 08:30
        long t1000 = t0800 + 120 * 60_000L; // Window end 10:00

        Spec spec = new Spec("window-2026-11-06-morning", t0745, t0800, t0830, t1000, 2, 10 * 60_000L);

        // Test 1: Double-Dose Lockout Shield Invariant
        State s0 = State.scheduled();
        Outcome openOut = StateMachine.evaluateWindowOpen(spec, s0, t0800);
        assertCondition(openOut.applied() && openOut.nextState().type() == StateType.DUE, "Should transition to DUE");

        long firstTapTime = t0800 + 2 * 60_000L; // 08:02
        Outcome confirm1 = StateMachine.evaluateSeniorConfirm(spec, openOut.nextState(), firstTapTime, "idem-1");
        assertCondition(confirm1.applied() && confirm1.nextState().type() == StateType.CONFIRMED, "Should confirm dose");
        assertCondition(confirm1.nextState().confirmedAt() == firstTapTime, "confirmedAt must equal 08:02");

        // Senior forgets and taps again 18 minutes later at 08:20
        long secondTapTime = t0800 + 20 * 60_000L;
        Outcome confirm2 = StateMachine.evaluateSeniorConfirm(spec, confirm1.nextState(), secondTapTime, "idem-2");
        assertCondition(confirm2.applied(), "Duplicate tap should be handled safely");
        assertCondition(confirm2.nextState().confirmedAt() == firstTapTime, "Original confirmedAt MUST NOT be overwritten");
        assertCondition(
            confirm2.effects().size() == 1 && confirm2.effects().get(0).type() == EffectType.TRIGGER_DOUBLE_DOSE_SHIELD,
            "Must trigger Double-Dose Prevention Shield on second tap"
        );
        System.out.println("[PASS] Invariant 1: Double-Dose Lockout Shield prevents accidental double-dosing.");

        // Test 2: Bounded Snooze & Escalation Deadline Clamping
        State dueState = openOut.nextState();
        Outcome snooze1 = StateMachine.evaluateSeniorSnooze(spec, dueState, t0800 + 5 * 60_000L); // 08:05 -> 08:15
        assertCondition(snooze1.applied() && snooze1.nextState().snoozeCount() == 1, "Snooze 1 applied");
        assertCondition(snooze1.nextState().snoozeUntil() == t0800 + 15 * 60_000L, "Snooze 1 wakes at 08:15");

        // Snooze 2 at 08:25 -> 08:25 + 10m = 08:35, MUST be clamped to escalationDeadline (08:30)!
        Outcome snooze2 = StateMachine.evaluateSeniorSnooze(spec, snooze1.nextState(), t0800 + 25 * 60_000L);
        assertCondition(snooze2.applied() && snooze2.nextState().snoozeCount() == 2, "Snooze 2 applied");
        assertCondition(
            snooze2.nextState().snoozeUntil() == spec.escalationDeadline(),
            "Snooze wakeup MUST be clamped to escalationDeadline (08:30)"
        );

        // Snooze 3 must be rejected
        Outcome snooze3 = StateMachine.evaluateSeniorSnooze(spec, snooze2.nextState(), t0800 + 26 * 60_000L);
        assertCondition(!snooze3.applied() && "SNOOZE_LIMIT_REACHED".equals(snooze3.rejectionReason()), "Snooze 3 must be rejected");
        System.out.println("[PASS] Invariant 2: Bounded Snooze & Escalation Deadline Clamping verified.");

        // Test 3: Escalation -> Late Senior Confirmation sends Caregiver All-Clear
        Outcome escalated = StateMachine.evaluateGraceExpired(spec, snooze2.nextState(), t0830, "idem-esc");
        assertCondition(escalated.applied() && escalated.nextState().type() == StateType.ESCALATED, "Should escalate at 08:30");

        Outcome lateConfirm = StateMachine.evaluateSeniorConfirm(spec, escalated.nextState(), t0830 + 4 * 60_000L, "idem-late");
        assertCondition(lateConfirm.applied() && lateConfirm.nextState().type() == StateType.CONFIRMED, "Late confirm transitions to CONFIRMED");
        assertCondition(
            lateConfirm.effects().stream().anyMatch(e -> e.type() == EffectType.OUTBOX_CAREGIVER_ALL_CLEAR),
            "Late confirm after escalation MUST emit CAREGIVER_ALL_CLEAR outbox event"
        );
        System.out.println("[PASS] Invariant 3: Late Confirmation after Escalation emits Caregiver All-Clear.");

        // Test 4: Deterministic Simulation Fuzzing (10,000 randomized command sequences)
        Random prng = new Random(0xC0FFEEL);
        int sequences = 10_000;
        for (int i = 0; i < sequences; i++) {
            State current = State.scheduled();
            long now = t0745 - 5 * 60_000L;
            Long firstConfirmedTimestamp = null;

            for (int step = 0; step < 25; step++) {
                now += prng.nextInt(10 * 60_000);
                int action = prng.nextInt(4);
                Outcome out = switch (action) {
                    case 0 -> StateMachine.evaluateWindowOpen(spec, current, now);
                    case 1 -> StateMachine.evaluateSeniorConfirm(spec, current, now, "idem-" + i + "-" + step);
                    case 2 -> StateMachine.evaluateSeniorSnooze(spec, current, now);
                    default -> StateMachine.evaluateGraceExpired(spec, current, now, "idem-esc-" + i + "-" + step);
                };

                State next = out.nextState();
                // Universal Invariant A: snoozeCount never exceeds maxSnoozes
                assertCondition(next.snoozeCount() <= spec.maxSnoozes(), "Snooze count exceeded maxSnoozes in seed " + i);
                // Universal Invariant B: snoozeUntil never exceeds escalationDeadline
                if (next.snoozeUntil() != null) {
                    assertCondition(next.snoozeUntil() <= spec.escalationDeadline(), "snoozeUntil exceeded escalationDeadline");
                }
                // Universal Invariant C: Once CONFIRMED, state never leaves CONFIRMED and confirmedAt never mutates
                if (firstConfirmedTimestamp != null) {
                    assertCondition(next.type() == StateType.CONFIRMED, "Left CONFIRMED state illegally!");
                    assertCondition(Objects.equals(next.confirmedAt(), firstConfirmedTimestamp), "confirmedAt mutated after confirmation!");
                } else if (next.type() == StateType.CONFIRMED) {
                    firstConfirmedTimestamp = next.confirmedAt();
                }
                current = next;
            }
        }
        System.out.println("[PASS] Invariant 4: Deterministic Simulation Testing (10,000 randomized sequences, 250,000 transitions) passed with 0 violations.");
    }
}
