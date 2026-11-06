---
name: zero-downtime-migration
description: >-
  Use this skill when the user wants to refactor legacy code, migrate a database schema,
  split or merge services, replace a dependency, or perform a zero-downtime production migration
  using Expand-and-Contract, Strangler Fig, or Branch-by-Abstraction patterns.
---

# Principal SWE Zero-Downtime Migration & Refactoring Playbook

At scale, "stop-the-world" rewrites and destructive schema changes cause outages. When invoked, design and execute incremental, reversible, zero-downtime migrations where **every intermediate commit is deployable and backward-compatible**.

---

## Playbook A: Zero-Downtime Database Schema Migration (Expand & Contract)

Never rename/drop a column, change a column type destructively, or add a blocking `NOT NULL` constraint without a default in a single deployment while old application instances are still running.

### Phase 1: Expand (Additive & Non-Blocking)
1. Add the new column(s), table(s), or index(es) non-blockingly (e.g., `CREATE INDEX CONCURRENTLY` in PostgreSQL, online DDL in MySQL).
2. New columns must be nullable or have safe non-locking defaults so existing running code continues writing without errors.
3. Deploy application code that **dual-writes** to both the old and new schema while still **reading from the old schema**.

### Phase 2: Backfill (Idempotent & Throttled)
1. Run a batched, idempotent background backfill job (`WHERE new_col IS NULL LIMIT 1000`) with sleep/rate-limiting to avoid saturating replication lag, WAL/binlog IOPS, or buffer pools.
2. Verify data parity between the old and new schema using a consistency checker query or shadow-read comparison.

### Phase 3: Switch Reads (Feature-Flagged)
1. Enable **Dark/Shadow Reads** (read from both old and new, compare results, log discrepancies, return old result).
2. Once zero discrepancies are confirmed under production load, flip the feature flag to read exclusively from the new schema.
3. Keep dual-writes active during the bake period so you can roll back reads instantly if an edge case appears.

### Phase 4: Contract (Cleanup)
1. Deploy code that stops writing to and referencing the old column/table.
2. Once no running application nodes reference the old schema, safely drop the legacy column/table/constraints.

---

## Playbook B: Code & Service Modernization (Branch by Abstraction & Strangler Fig)

### Step 1: Establish the Seam & Characterization Tests
1. Identify the minimal interface boundary ("seam") around the legacy component.
2. Write hermetic **characterization tests** capturing the exact current behavior (including quirks and edge cases) before changing a single line of implementation.

### Step 2: Introduce the Abstraction Router
1. Extract a clean domain interface/trait/port.
2. Wrap the existing legacy code as `LegacyProvider` implementing that interface. Verify 100% of tests pass.

### Step 3: Build `ModernProvider` & Shadow Verify
1. Implement the new logic in `ModernProvider` behind the same interface.
2. Introduce a `ShadowRouter` decorator that executes `LegacyProvider` synchronously (returning its result to the caller) while running `ModernProvider` asynchronously (with a strict timeout and error isolation) and emitting diff metrics whenever outputs diverge.

### Step 4: Gradual Traffic Ramp & Decommission
1. Ramp primary traffic via feature flag (`1% -> 10% -> 50% -> 100%`) while monitoring `SLIs` (error rate, `p99` latency, resource saturation).
2. After full cutover and bake period, delete `LegacyProvider`, remove the router wrapper, and remove dead feature flags.
