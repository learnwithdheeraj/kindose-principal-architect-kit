# KinDose — Intelligent Elder Medication Adherence & Family Caregiver Escalation Platform

**KinDose** (`com.kindose.app`) is an Android-first medication safety platform designed to prevent two critical daily failure modes for aging parents living independently: **missed critical doses** and **accidental double-dosing**.

Unlike general-purpose pill trackers that rely on easily dismissed notifications or complex multi-screen forms, KinDose splits the experience into two purpose-built surfaces governed by a deterministic, offline-first escalation engine:

1. **Senior Daily Appliance Surface:** A high-contrast (`WCAG AAA`), single-action check-in screen with sculptural pill visualizations, large confirmation controls, an immediate **Double-Dose Prevention Shield**, and a safe accidental-tap undo path.
2. **Caregiver Live Monitor & Escalation Console:** A real-time monitoring and schedule management console that automatically escalates unconfirmed doses after a 30-minute grace window via high-priority push (`FCM Priority 10`) and SMS fallback, requiring an audited resolution note when intervening.

---

## Core User Journeys (CUJs)

* **Zero-Trust Onboarding & 6-Digit Senior Device Pairing:**
  * Cold starts and cache-cleared boots enforce a strict Zero-Trust Authentication Gate (`Google CredentialManager`, `Biometric Passkey`, `Facebook OAuth`, `Apple ID`) or a validated **6-Digit Senior Pairing Code** (`e.g., 482-910`).
  * Authenticated sessions bind to both local encrypted preferences and an ephemeral cache sentinel (`kindose_active_session.nonce`), guaranteeing that clearing app cache or storage immediately revokes the session token with zero pre-selected accounts.
* **Senior Single-Action Confirmation & Double-Dose Shield:**
  * Displays the exact pill shape, color, clinical imprint code (`LUPIN 10`, `M 500`, `ELI 5`), and dosage instructions.
  * Tapping **Confirm Dose Taken** transitions the dose window in `<16ms` into a locked **Double-Dose Prevention Shield** (*"Confirmed — Do Not Take Again"*) while preserving an **Accidental Tap? Undo Confirmation** escape hatch.
* **30-Minute Caregiver Escalation & Audited Resolution:**
  * When a dose passes its 30-minute grace window unconfirmed, the state machine transitions to `ESCALATED`, alerting the linked caregiver with direct **Call Senior** and **Resolve Escalation** actions backed by mandatory clinical audit notes.
* **Full Medication Schedule Management:**
  * Caregivers can add custom medications (selecting pill geometry, clinical imprint, dosage, morning/afternoon/evening window, and instructions) with duplicate-schedule conflict detection, or remove discontinued medications.
* **Coherent Post-Login Profile & Account Switcher:**
  * Once authenticated, navigation dynamically reflects the active identity (`Profile` tab and verified provider pill) with one-tap account switching across providers or full session sign-out.

---

## Architecture & Engineering Rigor (`RFC-001`)

* **Pure Functional Core (`DoseEscalationStateMachine.kt`):**
  * All dose lifecycle transitions (`SCHEDULED -> DUE -> SNOOZED -> CONFIRMED | ESCALATED -> RESOLVED`) are modeled as closed algebraic data types (`sealed interface`) with deterministic guards and idempotency keys.
* **Atomic SQLite WAL + Transactional Outbox (`DataRepository.kt`):**
  * Every state transition atomically updates the `dose_windows` table, appends an immutable record to `audit_log`, and enqueues a signed payload in `sync_outbox` inside a single SQLite `IMMEDIATE` transaction—eliminating dual-write bugs.
* **Direct Boot & Doze-Mode Resilience:**
  * Configured with `android:directBootAware="true"`, exact alarm clock scheduling, and high-priority FCM wakeups so critical reminders survive device reboots and deep Android Doze windows.
* **Verified at Scale:**
  * Backed by unit tests covering all happy-path and wrong-path CUJs (`MainScreenViewModelTest`) and a **250,000-transition deterministic simulation suite** (`DoseEscalationSimulationTest`) verifying zero invariant violations across 1,000 simulated senior households.

---

## Project Structure

```text
├── app/src/main/kotlin/com/kindose/app/
│   ├── auth/          # Jetpack CredentialManager & OAuth / Passkey gateway
│   ├── core/          # Pure functional domain models & DoseEscalationStateMachine
│   ├── data/          # SQLite WAL repository, Transactional Outbox & Zero-Trust cache sentinel
│   ├── fcm/           # Priority-10 FCM escalation receiver & notification channels
│   └── ui/            # Jetpack Compose GM3 Expressive Senior & Caregiver surfaces
├── app/src/test/      # JUnit 4 + Coroutines test suite & 250k-transition property simulation
└── docs/rfcs/         # RFC-001 Architecture specification
```

---

## Build, Test & Run

### Prerequisites
* **JDK 17+**
* **Android SDK (`API 35`)**

### Run Unit & Simulation Tests
```bash
./gradlew testDebugUnitTest
```

### Build Debug APK
```bash
./gradlew assembleDebug
```

---

## License
Licensed under the [Apache License 2.0](LICENSE).
