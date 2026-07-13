# ADR-004 — Webhook idempotency via payload fingerprint

**Status:** accepted · **Date:** 2026-07-13

## Context

The simulator's events carry **no event id, no signature, no idempotency header** (verified by
capturing live traffic). Delivery may repeat (network retries, evaluator replaying requests,
bursts 2 ms apart). Re-applying an EXIT would duplicate revenue; re-applying a PARKED would
corrupt occupancy.

## Decision

Canonical **SHA-256 fingerprint** computed from normalized identifying fields:

```
ENTRY  : "ENTRY|"  + plate + "|" + entryTime(UTC ISO-8601)
PARKED : "PARKED|" + plate + "|" + lat(scale 6) + "|" + lng(scale 6)
EXIT   : "EXIT|"   + plate + "|" + exitTime(UTC ISO-8601)
```

Normalization: plate trim+uppercase; timestamps parsed to `Instant` then formatted canonically
(so `12:00:00Z`, `12:00:00.000Z` and zoneless-UTC `12:00:00` collide as intended); coordinates
rescaled to 6 decimals; `|` separator is outside the plate charset → unambiguous.

Stored in `processed_webhook_event.fingerprint` (`CHAR(64) UNIQUE`), **inserted in the same
transaction as the business change**, as its first statement.

## Behavior

| Scenario | Outcome |
|---|---|
| Exact replay (sequential) | fast-path SELECT hit → HTTP 200 `DUPLICATE`, zero side effects |
| Exact replay (concurrent) | second INSERT blocks on unique index → duplicate-key after first commit → `DUPLICATE`; at most one effect applied |
| Failed processing | rollback removes fingerprint → honest retry is processed normally |
| Same plate, *different* ENTRY (new entry_time) | different fingerprint → domain rule → 409 active-session-exists |
| Second EXIT with different exit_time | different fingerprint → 409 invalid transition (session already EXITED) |

The unique constraint — not the lookup, not any cache — is the correctness mechanism. No
in-memory dedup: it would not survive restarts nor multiple instances.

## Limitations

- Fingerprint identity is *payload* identity. A semantically-equal event with a different
  timestamp is treated as a new event — and then rejected by domain invariants, which are the
  real guardians of state (active-plate uniqueness, one-charge-per-session, spot discipline).
- The registry grows unboundedly; production would add TTL-based archival (documented, not
  implemented — out of scope).
