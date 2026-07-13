# 05 — Concurrency & Idempotency

## Threat scenarios (what we defend against)

1. Two `ENTRY` racing for the last global capacity slot.
2. Two `PARKED` racing for the same physical spot.
3. Two `EXIT` for the same session (double charge / double release / double decrement).
4. The same event delivered twice (burst or replay) — concurrently or sequentially.
5. Application restart mid-stream (reconciliation).

## Locking strategy (pessimistic; ADR-003)

MySQL InnoDB `SELECT ... FOR UPDATE` via Spring Data `@Lock(PESSIMISTIC_WRITE)`.

| Resource | Lock target | Used by |
|---|---|---|
| Global capacity | `garage_state` row (PK=1) | ENTRY (check+increment), EXIT (decrement) |
| Active session | `parking_session` row by `active_plate` | PARKED, EXIT |
| Physical spot | `parking_spot` row by coordinates / id | PARKED (occupy), EXIT (release) |

**Global lock order** (prevents deadlocks — always acquired in this order, never reversed):

```
1. processed_webhook_event  (INSERT fingerprint — idempotency gate, first statement)
2. parking_session          (by active_plate)
3. parking_spot
4. garage_state
```

- ENTRY: fingerprint → insert session (unique `active_plate` guards duplicates) → lock garage_state → check capacity → increment (or rollback all if full).
- PARKED: fingerprint → lock session → lock spot → sector occupancy count → freeze price → occupy.
- EXIT: fingerprint → lock session → lock spot (if any) → insert charge → lock garage_state → decrement.

Transactions are short (single aggregate + counters, no HTTP calls inside — the simulator is
called only during startup sync, outside any business transaction). Contention is bounded by
the single-garage cardinality (30 spots, 1 state row): correctness beats throughput here, and
the lock hold time is a few milliseconds.

Optimistic `@Version` columns exist on `parking_session`/`parking_spot`/`garage_state` as a
backstop against any accidental lock-free write path, not as the primary mechanism.

Why not `synchronized`/JVM locks: correct only within one JVM; the database is the single
serialization point that survives horizontal scaling and restarts.

InnoDB deadlock detection remains as safety net: a victim transaction rolls back atomically
(fingerprint row included, so a retry of the same event is *not* treated as duplicate) and maps
to HTTP 503 with `Retry-After` semantics documented.

## Idempotency (ADR-004)

Events carry no id (discovery §2.3) → canonical **SHA-256 fingerprint** over normalized fields:

```
ENTRY  : "ENTRY|"  + plate + "|" + entryTimeIso
PARKED : "PARKED|" + plate + "|" + lat6 + "|" + lng6
EXIT   : "EXIT|"   + plate + "|" + exitTimeIso
```

Normalization: plate trimmed/uppercased; times as UTC `Instant` ISO-8601; coordinates at scale 6
(simulator precision); `|` separator (excluded from plate charset).

Mechanics:

- The fingerprint row is **inserted in the same transaction** as the business change —
  first statement of the unit of work.
- Sequential duplicate: fingerprint SELECT hit → short-circuit → HTTP 200 `DUPLICATE`, zero side effects.
- Concurrent duplicate: both miss the SELECT; second INSERT blocks on the unique index until
  the first commits → duplicate-key → mapped to `DUPLICATE`, transaction rolled back → at most
  one application of the effect. The unique constraint is the last line of defense; the lookup
  is just the fast path.
- Failure rollback removes the fingerprint too → a retry after a genuine failure is processed,
  not swallowed as duplicate.

Semantic conflicts are *not* duplicates: a **new** ENTRY (different `entry_time`) for a plate
with an active session is a different fingerprint → hits the domain rule → 409
`ActiveSessionAlreadyExistsException`. A second EXIT with a different `exit_time` → 409 invalid
transition. Only exact replays return 200/DUPLICATE.

Limitations (documented): fingerprint scope is per-payload; if the simulator re-emitted a
*semantically identical* event with a different timestamp it would be treated as a new event —
domain invariants (active-plate uniqueness, charge uniqueness, spot discipline) still keep the
state consistent, which is the property that matters.

## Startup reconciliation

`garage_state.active_vehicle_count` is recomputed from `COUNT(parking_session WHERE status IN
(ENTERED, PARKED))` inside the sync transaction — the counter is derived state and self-heals
on every boot.
