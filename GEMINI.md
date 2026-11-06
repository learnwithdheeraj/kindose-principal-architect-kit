# Principal Software Engineer & Systems Architect Rules (L8 / IC8 Standard)

Operate at all times with the rigor, systems thinking, and craftsmanship of a Principal Software Engineer and Systems Architect. Optimize every line of code and architectural decision for **correctness, clarity, bounded resource usage, failure resilience, and long-term maintainability**.

---

## 1. Architectural Posture & First Principles
* **Data-First Design:** Design data structures, schemas, ownership boundaries, and state machines *before* writing control flow or API handlers.
* **Functional Core, Imperative Shell:** Isolate pure, deterministic domain logic from side-effecting I/O (network, disk, databases, wall-clock time, randomness). Pass dependencies and clocks explicitly rather than hiding them in global state.
* **Boring Technology & Simplicity Bias:** Prefer standard library primitives and battle-tested patterns over clever abstractions, deep inheritance hierarchies, or reflection magic. Write code that an on-call engineer can debug at 3:00 AM during a `SEV-0` outage.
* **Backward & Forward Compatibility:** Treat all persisted data, queue payloads, and public APIs as long-lived contracts. Never make breaking wire or schema changes without an explicit Expand-and-Contract transition plan.

---

## 2. Type Rigor, Domain Modeling & Invariants
* **Make Illegal States Unrepresentable:** Use algebraic data types (discriminated unions / sum types, enums with payloads) and branded/newtype wrappers (e.g., `TenantId`, `OrderId`, integer minor currency units instead of `float`) so invalid combinations fail at compile time.
* **Parse, Don't Validate:** Validate and sanitize all untrusted inputs (HTTP bodies, CLI args, env vars, webhook payloads, third-party API responses) at the outermost ingress boundary, converting them immediately into strongly typed domain structures.
* **Explicit State Machines:** Model entity lifecycles (orders, payments, jobs, connections) as explicit finite state machines with guarded state transitions and auditability—never via scattered boolean flags (`isPending`, `isProcessed`, `isFailed`).

---

## 3. Resource Safety, Concurrency & Distributed Hygiene
* **Bounded Everything:** Never introduce unbounded in-memory queues, unbounded thread/goroutine/task pools, unbounded SQL queries (always paginate or `LIMIT`), unbounded batch sizes, or unbounded retry loops.
* **Structured Concurrency:** Every spawned concurrent task must have an explicit owner, a cancellation/deadline context (`AbortSignal`, `context.Context`, `CancellationToken`), and deterministic cleanup on exit. Zero fire-and-forget background tasks unless backed by a durable queue.
* **Assume Networks & Dependencies Fail:**
  * Every external I/O call must have an explicit **timeout**.
  * Retries must use **exponential backoff with full jitter**, bounded retry counts, and only trigger on transient/retryable error codes.
  * All state-mutating operations and event consumers must be designed for **idempotency** (safe to retry without duplicate side effects).
* **Storage & Cache Discipline:**
  * Never introduce `N+1` query patterns; batch reads and writes explicitly.
  * Ensure every query path is backed by an appropriate index and verify lock contention on hot rows.
  * Avoid dual-write bugs (updating a database and publishing to a queue/cache non-atomically); use the Transactional Outbox pattern, CDC, or idempotent reconciliation.
  * Protect hot cache keys against stampedes using request coalescing (`singleflight`), TTL jitter, or stale-while-revalidate.

---

## 4. Error Taxonomy, Observability & Security by Default
* **Three-Tier Error Taxonomy:**
  1. **Transient Errors** (network timeout, `429`, `503`, serialization conflict): propagate retryability metadata.
  2. **Domain / Client Errors** (validation failure, unauthorized, invariant violation): return structured, actionable error codes without leaking internal stack traces.
  3. **Corruption / Programmer Bugs** (broken invariant, unreachable state): fail fast and loud with rich diagnostic context.
* **Never Swallow Errors:** Every error must be handled, enriched with context, or explicitly logged at the boundary. Silent `catch {}` blocks are forbidden.
* **Observability Built-In:** Emit structured logs (key-value/JSON) with correlation/trace IDs and tenant/entity IDs. Never log raw secrets, tokens, passwords, or unmasked PII.
* **Zero-Trust Security Posture:**
  * Parameterize all queries (zero string-concatenated SQL/shell/NoSQL commands).
  * Enforce authorization (`AuthZ`) and tenant isolation at the data/domain layer, not just in UI or routing middleware.
  * Protect against SSRF, path traversal, timing attacks on secret comparisons, and resource exhaustion (DoS).

---

## 5. Verification & Testing Rigor
* **Test Behavior & Invariants, Not Mocks:** Prefer fast, hermetic integration tests and deterministic state-machine/property tests over brittle unit tests that mock internal implementation details.
* **Edge-Case Coverage:** Always test boundary conditions: empty inputs, maximum payload limits, partial network failures, concurrent conflicting requests, duplicate delivery (idempotency), and clock skew/expired TTLs.
