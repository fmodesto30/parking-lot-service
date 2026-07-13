# PLAN — Estapar Backend Developer Test

Working branch: `develop` (merged into `main` at delivery). One commit per coherent slice.
Each phase ends with: relevant tests green, PLAN/HANDOFF updated, no broken build left behind.

| # | Phase | Scope | Done when | Status |
|---|---|---|---|---|
| 0 | Discovery | Read document; run + reverse-engineer simulator; specs 00–08; ADR-001..005; PLAN/HANDOFF; git init | Real contract evidence recorded in `docs/specs/00-challenge-analysis.md` | ✅ done |
| 1 | Bootstrap | Maven wrapper, pom (Java 21, Boot 3.5.x, Spotless, Enforcer, JaCoCo, Surefire/Failsafe), app skeleton on :3003, compose (app+MySQL, healthchecks), Flyway V1–V7, `.env.example`, `.gitignore` | `./mvnw verify` green (empty test), `docker compose config` valid, Flyway migrates on Testcontainers MySQL | ⬜ pending |
| 2 | Domain + pricing | VOs (LicensePlate, Money, Coordinates, OccupancyRate, AppliedPrice), ParkingSession state machine, OccupancyPricingPolicy, BillingCalculator, Clock discipline | Unit matrix from spec 06 green (billing boundaries, pricing bands, transitions, normalization) | ⬜ pending |
| 3 | Garage sync | RestClient adapter (timeouts, limited retry+backoff), sync use case with idempotent upsert, garage_state seed+reconcile, readiness indicator | WireMock suite green; double-sync IT proves idempotency | ⬜ pending |
| 4 | ENTRY | fingerprint gate, session creation, global capacity under pessimistic lock, conflict rules | Unit + IT green incl. duplicate and full-garage; concurrent last-slot race IT | ⬜ pending |
| 5 | PARKED | spot lookup by coords, session+spot locks, sector gating, price freeze | IT green incl. same-spot race, sector-full at 100%, band snapshot correctness | ⬜ pending |
| 6 | EXIT + revenue | billing from snapshots, immutable charge, spot release, counter decrement, GET /revenue with business zone | IT green incl. double-EXIT race, midnight boundary test, revenue query | ⬜ pending |
| 7 | Web API | webhook controller + conditional validation, sealed events + pattern-matching dispatch, ProblemDetail advice, correlation filter, OpenAPI | MockMvc suite green for all response codes from spec 03 | ⬜ pending |
| 8 | Observability + security | metrics, structured logs w/ masked plates, actuator exposure, error hardening | Metrics visible in IT; log names match spec 07 | ⬜ pending |
| 9 | Evaluator UX | Postman collection + environment (with tests), garage-api.http, README PT-BR with Mermaid diagrams, smoke script for real sim | Collection runs green against compose stack | ⬜ pending |
| 10 | CI + final | GitHub Actions, ArchUnit rules, e2e (charged + free), adversarial review, `./mvnw clean verify` + compose build evidence, push | Acceptance criteria 08 fully checked; repo pushed | ⬜ pending |

## Delivery gate (from spec 08)

`./mvnw clean verify` ✦ `docker compose config` ✦ `docker compose build` ✦ manual flow against
real simulator — all executed with output captured in the final report.
