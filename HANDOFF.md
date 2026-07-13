# HANDOFF — Estapar parking-lot-service

> Session log for continuity. Newest facts first. Full narrative: `PLAN.md` + `docs/`.

## Current state (2026-07-13)

- **All 10 phases complete.** `./mvnw clean verify` green: 139 tests (unit + Testcontainers
  MySQL integration + deterministic concurrency + e2e + ArchUnit). `docker compose config`
  valid; `docker compose build` succeeds.
- Repo: `E:\Estapar\parking-lot-service` → `https://github.com/fmodesto30/parking-lot-service`.
- Branch model: work on `develop`, merged `--no-ff` into `main` at delivery (workspace
  git-workflow convention; PRs impossible with local PAT — Contents:write only).
- Commits (develop): discovery → bootstrap → domain → persistence/sync → event use cases →
  web API → metrics → evaluator docs → CI/ArchUnit/e2e.

## Environment facts (this machine)

- JDK 21 = `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` (installed via winget
  during this session; JDK 25 also present — `mvnw` picks `JAVA_HOME`).
- Maven via wrapper (`only-script`, Maven 3.9.11); no global mvn.
- Docker Desktop running; pre-existing containers (interior-studio-bff, gpt-chrome-bridge,
  qdrant) must not be touched.
- Discovery containers used: `garage-sim-disc` + `hook` on network `estapar-disc`
  (disposable; removed after discovery).

## Key decisions (details in docs/adr/)

1. **ADR-001** modular monolith, ArchUnit-enforced boundaries.
2. **ADR-002** ENTRY = global admission (no sector known — verified); price frozen at PARKED
   pre-occupation; ENTERED→EXITED = no charge.
3. **ADR-003** pessimistic row locks; global order fingerprint→session→spot→garage_state.
4. **ADR-004** SHA-256 payload fingerprint + unique constraint, same-transaction insert.
5. **ADR-005** UTC storage; zoneless timestamps = UTC (simulator behavior); business zone
   America/Sao_Paulo; charged_at = exit_time.

## Simulator ground truth (verified live — full evidence in docs/specs/00)

- Listens on **:3000** (image's ExposedPorts 8080 is wrong); Rust binary.
- Events start only **after** `GET /garage` is called.
- Env vars that work: `CLIENT_WEBHOOK_URL`, `ENTRY_INTERVAL_SECS`, `EXIT_INTERVAL_SECS`,
  `SIMULATOR_PORT`. (`EXTERNAL_API_URL` in image config is ignored by the binary.)
- `/garage` sends `base_price` snake_case + extra fields; timestamps zoneless UTC.
- Webhook down → error log every ~5s, keeps running.

## Important commands

```bash
./mvnw clean verify                    # full build: unit + IT (Testcontainers MySQL) + ArchUnit
./mvnw test                            # unit only
docker compose up --build              # app :3003 + MySQL :3306
docker compose --profile simulator up  # + simulator wired inside the network (any OS)
scripts/smoke-simulator.ps1|.sh        # end-to-end against real simulator
```

## Risks / pendências

- Phases 1–10 not yet implemented (tracked in PLAN.md).
- `processed_webhook_event` grows unboundedly (archival = production evolution, documented).
- Simulator's `open_hour`/`close_hour`/`duration_limit_minutes` intentionally out of scope.
- Machine gotcha to watch: java.exe loopback/AF_UNIX quirks on this Windows box — if the app
  won't bind locally, run it via compose (Linux container); tests use Testcontainers TCP.

## Tests executed (so far)

- None yet (no code). Discovery evidence: live captures in docs/specs/00.
