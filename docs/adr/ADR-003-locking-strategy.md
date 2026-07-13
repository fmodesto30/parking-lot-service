# ADR-003 — Pessimistic locking for capacity, spots and sessions

**Status:** accepted · **Date:** 2026-07-13

## Context

Three hot invariants: global capacity never exceeded; a spot never double-occupied; a session
never double-exited (double charge/release/decrement). Events can arrive in bursts
(observed: PARKED and EXIT 2 ms apart) and duplicated. Contention domain is tiny (one
`garage_state` row, 30 spots), transactions are short, and MySQL is already the single shared
state.

## Decision

**Pessimistic row locks** (`SELECT … FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)`) on the rows
that carry invariants, with a fixed global acquisition order:

```
fingerprint INSERT → parking_session (active_plate) → parking_spot → garage_state
```

Optimistic `@Version` columns stay on the same tables as a backstop only. Unique constraints
(`active_plate`, `(lat,lng)`, `charge.session_id`, `fingerprint`) are the last line of defense.

## Rationale

- Check-then-act sequences (capacity check → increment; availability check → occupy) need
  serialization. Pessimistic locks make them trivially correct and easy to reason about.
- Optimistic locking would turn every lost race into retry loops on a path the simulator does
  not retry; with bursts hitting the same rows, retries would be the common case, not the
  exception. Contention here is *expected*, which is exactly when pessimistic wins.
- JVM-level `synchronized` would only serialize one instance; the DB lock survives horizontal
  scale-out and restarts.

## Deadlock prevention

- Single global lock order (above), documented and enforced by code review + concurrency tests.
- ENTRY inserts the session **before** locking `garage_state` so no transaction ever holds the
  state row while waiting on session index locks held by an EXIT of the same plate.
- Transactions are milliseconds long; no HTTP inside transactions (simulator I/O happens only
  in the startup sync, outside business transactions).
- InnoDB deadlock detection is the safety net: victim rolls back atomically (fingerprint
  included) → the event can be retried without being mistaken for a duplicate; surfaced as 503.

## Consequences

- Slight serialization of ENTRY throughput on the single `garage_state` row — irrelevant at
  this cardinality and honest about the physics: a single gate group *is* a serial resource.
- Lock-order discipline is a review invariant; concurrency ITs exercise the three races
  directly against real MySQL.
