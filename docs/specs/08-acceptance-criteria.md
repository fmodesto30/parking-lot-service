# 08 — Acceptance Criteria (delivery gate)

The checklist below mirrors the challenge + engineering bar. Delivery is complete only when
every box is checked with real evidence (command output, test run, or file reference).

## Functional
- [ ] `GET /garage` consumed at startup; sectors/spots persisted idempotently
- [ ] `POST /webhook` ENTRY/PARKED/EXIT full lifecycle works against the real simulator
- [ ] `GET /revenue?date=&sector=` returns `{amount, currency: BRL, timestamp}`
- [ ] 30-minute grace rule exact at boundary (30:00 free, 30:01 → 1h)
- [ ] Ceiling on total duration (60:01 → 2h, 120:01 → 3h)
- [ ] Dynamic pricing bands exact at 25/50/75/100% with occupancy measured *before* occupation
- [ ] Sector closes at 100%; garage closes at global capacity
- [ ] ENTERED→EXITED (never parked) supported without charge

## Consistency
- [ ] Same spot cannot be double-occupied (verified under concurrency)
- [ ] Global capacity never exceeded (verified under concurrency)
- [ ] At most one charge per session (unique constraint + concurrent EXIT test)
- [ ] Exact duplicate events are 200/DUPLICATE with zero side effects (sequential + concurrent)
- [ ] Counter reconciliation on restart

## Engineering
- [ ] Java 21, Spring Boot 3.5.x, MySQL 8, Flyway-only schema (`ddl-auto=validate`)
- [ ] Domain free of Spring/JPA; ArchUnit enforces layer rules
- [ ] ProblemDetail + correlation id on all error paths
- [ ] Unit + integration (Testcontainers MySQL) + concurrency + e2e green: `./mvnw clean verify`
- [ ] `docker compose config` valid; `docker compose build` succeeds; app runs in compose
- [ ] CI (GitHub Actions) green without the real simulator
- [ ] OpenAPI served; Postman collection + `.http` file work against a running stack
- [ ] README (PT-BR) + 5 ADRs complete and truthful to the code
- [ ] No TODOs in mandatory scope, no ignored tests without justification, no secrets, no stray files

## Final commands (must pass, evidence in report)
```bash
./mvnw clean verify
docker compose config
docker compose build
# manual flow with real simulator (documented in README)
```
