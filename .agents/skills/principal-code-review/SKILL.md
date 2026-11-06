---
name: principal-code-review
description: >-
  Use this skill when the user asks for a deep code review, PR audit, production-readiness check,
  security/concurrency audit, or refactoring review at a Principal Software Engineer (L8/IC8) standard.
---

# Principal SWE Production-Readiness & Code Review Playbook

When invoked, perform a rigorous, multi-pass Principal Engineer audit of the target code or diff. Skip trivial formatting nitpicks that a linter catches; focus relentlessly on **correctness, invariants, concurrency hazards, distributed failure modes, security boundaries, performance cliffs, and maintainability**.

---

## Multi-Pass Audit Protocol

### Pass 1: Domain Modeling, Invariants & State Machines
* Are illegal states representable? Can primitive types (`string`, `number`, `float`, `boolean` flags) be replaced with discriminated unions, enums, or branded/newtype wrappers?
* Are untrusted inputs parsed and validated at the ingress boundary (`Parse, Don't Validate`)?
* Are entity lifecycles modeled as explicit state machines with guarded transitions?
* Is pure business logic cleanly separated from I/O side effects (`Functional Core, Imperative Shell`)?

### Pass 2: Concurrency, Memory & Resource Bounds
* Are there any **unbounded resources**? (Unbounded arrays/slices in memory, unpaginated `SELECT` queries, unbounded goroutines/tasks, unbounded channels/queues).
* Are there race conditions, time-of-check to time-of-use (`TOCTOU`) bugs, deadlocks (inconsistent lock acquisition order), or lock contention across network I/O?
* Are spawned tasks governed by **structured concurrency** with explicit cancellation/deadline propagation and guaranteed resource cleanup (`defer`, `try/finally`, `using`, `Drop`)?

### Pass 3: Distributed Systems, Network & Storage Pathologies
* **Timeouts & Retries:** Does every external RPC/HTTP/DB call have an explicit timeout? Do retries use exponential backoff with jitter and respect a retry budget?
* **Idempotency:** If a request, webhook, or queue message is delivered twice (or concurrently), will it corrupt state, double-charge, or emit duplicate side effects?
* **Dual-Write Hazards:** Does the code write to a database and publish to a broker/cache/external API without a Transactional Outbox, Saga, or idempotent recovery mechanism?
* **Database & Cache Hygiene:**
  * Are there `N+1` queries inside loops?
  * Are queries missing composite indexes or scanning large tables?
  * Are transactions held open across slow network calls?
  * Are hot cache keys vulnerable to stampedes (missing `singleflight` or TTL jitter) or stale cache overwrites?

### Pass 4: Security, AuthZ & Multi-Tenant Isolation
* Is authorization (`AuthZ`) and `tenant_id` scoping enforced at the data-access/domain layer (preventing IDOR / cross-tenant data leakage)?
* Are there injection vectors (SQL, command, template, SSRF, path traversal, prototype pollution)?
* Are secrets compared in constant time, and is sensitive data (tokens, passwords, PII) excluded from logs and error responses?

### Pass 5: Observability, Error Taxonomy & Test Rigor
* Are errors categorized cleanly (Transient/Retryable vs. Client/Domain vs. Fatal) without silent `catch` blocks?
* Do logs include structured correlation context (`request_id`, `trace_id`, `tenant_id`, `entity_id`)?
* Do tests verify **invariants, failure modes, and concurrent/duplicate edge cases**, rather than merely asserting that mocks were called?

---

## Output Format for Reviews

Structure your findings by severity:
1. **`[CRITICAL / BLOCKER]`** — Bugs that cause data corruption, security vulnerabilities, outages, race conditions, or unbounded resource leaks in production. Include the exact failure scenario and a concrete code fix.
2. **`[MAJOR / ARCHITECTURE]`** — Flaws in domain modeling, missing idempotency/timeouts, `N+1` queries, backward-incompatibility, or missing failure recovery.
3. **`[MINOR / CRAFTSMANSHIP]`** — Simplifications, dead-code removal, readability improvements, or missing edge-case tests.
4. **`[VERDICT & INVARIANT SUMMARY]`** — Overall production readiness assessment and key invariants verified.
