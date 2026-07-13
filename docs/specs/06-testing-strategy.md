# 06 — Testing Strategy

Pyramid: many unit tests (domain/pricing/billing — no Spring, no I/O), a focused set of
integration tests (Testcontainers MySQL 8 — real engine, real locks, real constraints),
two end-to-end scenario tests, and a handful of WireMock tests for the simulator client.
No H2 anywhere — MySQL semantics (locking, unique-with-NULLs) are part of what's under test.

## Naming & layout

```
*Test.java  → unit (Surefire)
*IT.java    → integration/e2e (Failsafe, Testcontainers)
```

AssertJ everywhere; `assertAll`-style grouped assertions via AssertJ soft assertions where
several properties of one result are checked; `isEqualByComparingTo` for BigDecimal.

## Unit matrix

**Billing boundaries** (Duration → hours): 29m59s→0; 30m00s→0; 30m01s→1; 59m59s→1; 60m00s→1;
60m01s→2; 119m59s→2; 120m00s→2; 120m01s→3.

**Pricing bands** (occupancy → multiplier): 0%→0.90; 24.99%→0.90; 25%→1.00; 49.99%→1.00;
50%→1.10; 74.99%→1.10; 75%→1.25; 99.99%→1.25; 100%→closed.

**Session state machine**: valid ENTRY; PARKED without ENTRY; EXIT on ENTERED (no charge);
EXIT on PARKED; transitions after EXITED; exitTime < entryTime.

**Normalization**: lowercase/whitespace plates; coordinate scales; fingerprint determinism and
sensitivity (each field flips the hash).

## Integration (Testcontainers MySQL)

- Flyway migrations apply cleanly on pristine MySQL 8.
- Repositories: unique constraints fire (`active_plate`, `fingerprint`, `session_id`, coords).
- Sync idempotency: two syncs, no duplicates; occupied spot state survives re-sync;
  counter reconciliation.
- Revenue query: charges around midnight in `America/Sao_Paulo` land on the correct business date.
- Charge immutability: no update path.

## Concurrency (mandatory, deterministic — no sleep-based coordination)

`ExecutorService` + `CountDownLatch` start-barrier; assertions on final DB state:

1. Last global slot: capacity 1, two concurrent ENTRY → exactly one session, count == 1.
2. Same spot: two plates, one spot → exactly one occupies; other gets 409.
3. Double EXIT: one PARKED session, two concurrent EXIT → one charge, spot released once,
   counter decremented once.
4. Same fingerprint concurrently → effect applied at most once, one PROCESSED + one DUPLICATE.

## Simulator client (WireMock)

Valid payload (real snake_case shape); `basePrice` alias; timeout; connection refused; 500;
malformed JSON; retry limited with backoff; recovery after transient failure; readiness stays
DOWN when sync never succeeds.

## End-to-end (SpringBootTest + Testcontainers, full wiring)

- **Charged path**: sync → ENTRY → PARKED (price frozen at band) → EXIT (>30min) → charge →
  spot free → `GET /revenue` returns amount/currency/date.
- **Free path**: ENTRY → PARKED → EXIT ≤30min → charge 0.00 → revenue 0.00.

## Out of scope

Live simulator in CI (a local smoke script exists instead); load testing; mutation testing.
