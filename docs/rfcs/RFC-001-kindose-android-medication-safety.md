# RFC-001: KinDose — Android-First Elder Medication Safety & Deterministic Escalation Engine

| Metadata | Details |
| :--- | :--- |
| **Status** | `APPROVED FOR V1 IMPLEMENTATION` |
| **Author** | Principal Systems Architect (`L8` / `IC8`) |
| **Target Platform** | Android (`API 26+` / Android 8.0+, Kotlin, Jetpack Compose, SQLite WAL) |
| **Core Pattern** | Local-First Functional Core + Transactional Outbox + Deterministic Escalation State Machine |

---

## 1. Executive Summary & Problem Statement
Aging parents living independently face two life-threatening daily medication failure modes: **missed critical doses** (e.g., anticoagulants, insulin, antihypertensives) and **accidental double-dosing** (forgetting they already took a pill 20 minutes ago and taking a second one). Existing reminder apps fail because they rely on standard best-effort Android notifications (silently killed by OEM battery savers/Doze mode), require complex multi-screen navigation unsuitable for seniors with tremors or low vision, and lack deterministic family escalation when a dose is missed.

**KinDose** solves this with a radical **two-surface, offline-first architecture**:
1. **Senior Surface (Zero-Login, Single-Action Appliance Mode):** A full-screen high-contrast (`WCAG AAA`) card triggered by Doze-exempt exact alarm clocks (`AlarmManager.setAlarmClock`), featuring a single giant confirmation button that immediately locks into a **Double-Dose Prevention Shield** once tapped.
2. **Deterministic Caregiver Escalation Engine:** An append-only, idempotent state machine that escalates unconfirmed doses after a configurable grace window (`e.g., +30m`) via multi-channel push + SMS fallback.

---

## 2. Explicit Finite State Machine (`Functional Core`)

Illegal states are unrepresentable. A `DoseWindow` lifecycle is modeled as a closed algebraic data type with strictly guarded transitions:
* `SCHEDULED` -> `DUE` | `CONFIRMED`
* `DUE` -> `CONFIRMED` | `SNOOZED` (max 2 snoozes, clamped to escalation deadline) | `ESCALATED`
* `SNOOZED` -> `DUE` | `CONFIRMED` | `ESCALATED`
* `ESCALATED` -> `CONFIRMED` (emits `CAREGIVER_ALL_CLEAR` event) | `RESOLVED_BY_CAREGIVER` | `MISSED_EXPIRED`
* `CONFIRMED` -> `CONFIRMED` (idempotent no-op + triggers **Double-Dose Prevention Shield** warning!)
