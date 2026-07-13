# 00 — Challenge Analysis & Simulator Discovery

Source of truth: `Teste Back Java -kotlin (2).docx` — **Estapar Backend Developer Test (V1.4)**.
This document records what the challenge requires and, critically, what the **real simulator**
(`cfontes0estapar/garage-sim:1.0.0`) actually does — verified live on 2026-07-13.

## 1. Challenge requirements (from the document)

- Java 21 (or Kotlin 2.1.x), Spring (or Micronaut), MySQL, git. AI allowed.
- Start simulator: `docker run -d --network="host" cfontes0estapar/garage-sim:1.0.0`.
- On startup, fetch garage configuration via `GET /garage` and store sectors/spots.
- Accept webhook POSTs at `http://localhost:3003/webhook` for `ENTRY`, `PARKED`, `EXIT` — happy path responds **HTTP 200**.
- REST API: `GET /revenue` — revenue by sector and date. Response: `{ "amount": 0.00, "currency": "BRL", "timestamp": "..." }`.
- Billing: first 30 minutes free; beyond 30 minutes charge a fixed hourly rate **including the first hour** (sector `basePrice`, round up).
- If the garage is full, reject new entries until a spot frees up.
- Dynamic pricing **at entry**, by sector occupancy: `<25% → −10%`, `<50% → 0%`, `<75% → +10%`, `<100% → +25%`; at 100% the sector closes until a parked car leaves.
- Garages have a **single gate group** at the entrance; sectors are *logical* divisions, not physical ones.

## 2. Simulator discovery — verified evidence

Method: image inspected with `docker inspect`; binary grepped for configuration strings;
simulator run on an isolated Docker network with a capture HTTP server receiving its webhook calls.

### 2.1 Image & process facts

| Fact | Evidence |
|---|---|
| Implementation | Rust binary `garage-simulator` (tracing logs `garage_simulator`), Debian-based image, user `garage` |
| **Actual listen port: `3000`** | Log: `Simulator starting on 0.0.0.0:3000`. The image declares `ExposedPorts: 8080/tcp` — **misleading; nothing listens on 8080** |
| Webhook target default | `http://localhost:3003/webhook` (hardcoded default in binary) |
| `EXTERNAL_API_URL` env in image config | **Ignored by the binary** (verified: set in container env, simulator still posted to `localhost:3003`) |
| **Effective env vars (from binary strings, verified live)** | `CLIENT_WEBHOOK_URL` (overrides webhook target — **verified working**), `ENTRY_INTERVAL_SECS`, `EXIT_INTERVAL_SECS`, `SIMULATOR_PORT` |
| Simulation start trigger | Log: `Simulation started after /garage was called.` — **no events are emitted until someone calls `GET /garage`** |
| Behavior while webhook is down | Logs `Failed to send entry request: error sending request for url (...)` every ~5s; the simulator keeps running (no crash, no backpressure on `/garage`) |
| Response handling | On HTTP 200: `Entry successful for plate: ...`. No evidence of re-delivery of acknowledged events |

### 2.2 Real `GET /garage` payload (differs from the document example)

Real response (captured; truncated to one spot per sector):

```json
{
  "garage": [
    { "sector": "A", "base_price": 40.5, "max_capacity": 10,
      "open_hour": "00:00", "close_hour": "23:59", "duration_limit_minutes": 1440 },
    { "sector": "B", "base_price": 4.1, "max_capacity": 20,
      "open_hour": "08:00", "close_hour": "23:59", "duration_limit_minutes": 60 }
  ],
  "spots": [
    { "id": 1,  "sector": "A", "lat": -23.561684, "lng": -46.655981, "occupied": false },
    { "id": 11, "sector": "B", "lat": -23.561484, "lng": -46.655781, "occupied": false }
  ]
}
```

Deviations from the document example:

| Document says | Simulator actually sends | Consequence |
|---|---|---|
| `basePrice` (camelCase) | **`base_price`** (snake_case) | Client DTO maps snake_case and also accepts `basePrice` via `@JsonAlias` for document compatibility |
| — | extra fields `open_hour`, `close_hour`, `duration_limit_minutes`, `spots[].occupied` | Parsed leniently and ignored (out of the challenge's functional scope); `occupied` is ignored because **our sessions are the source of truth for occupancy** |
| single sector example | 2 sectors: A (10 spots, R$ 40.50), B (20 spots, R$ 4.10) | `max_capacity` matches actual spot count for both sectors (10/10, 20/20) |

### 2.3 Real webhook events (captured verbatim)

```text
16:02:11.200  POST /webhook  {"license_plate":"WDE56675","entry_time":"2026-07-13T16:02:11","event_type":"ENTRY"}
16:03:41.219  POST /webhook  {"license_plate":"WDE56675","lat":-23.561504,"lng":-46.655801,"event_type":"PARKED"}
16:03:41.221  POST /webhook  {"license_plate":"WDE56675","exit_time":"2026-07-13T16:02:21","event_type":"EXIT"}
16:03:46.258  POST /webhook  {"license_plate":"BHE56592","entry_time":"2026-07-13T16:03:46","event_type":"ENTRY"}
```

Findings that shape the design:

1. **Timestamps come without zone or millis** (`2026-07-13T16:02:11`), unlike the document
   example (`2025-01-01T12:00:00.000Z`). Cross-checking with the simulator's own UTC log
   timestamps shows the values are **UTC wall-clock**. The API therefore accepts both
   ISO-8601 with offset and zoneless local-date-time (interpreted as UTC). See ADR-005.
2. **`PARKED` carries no timestamp** — only plate + coordinates. `parked_at` is recorded with
   the application `Clock`.
3. **Event delivery time ≠ event time.** The `EXIT` arrived 90s after `ENTRY` delivery but its
   `exit_time` is only 10s after `entry_time` (and *earlier* than the moment `PARKED` was
   delivered). Billing must use the embedded `entry_time`/`exit_time`, never arrival time.
4. **Events can arrive in a burst** (`PARKED` and `EXIT` 2 ms apart) — handlers must tolerate
   back-to-back processing and be safe under concurrent delivery.
5. **No event id, no signature, no idempotency header** — deduplication must be derived from
   the payload itself (fingerprint; see ADR-004).
6. `ENTRY` events give **no sector/spot information** — the sector is only knowable at
   `PARKED` (coordinates). This is the central ambiguity of the challenge; see ADR-002.

### 2.4 Running the simulator on Windows/macOS (verified alternative)

`--network="host"` behaves as documented only on Linux. On Docker Desktop (Windows/macOS) the
container's `localhost` is not the host. Two **verified** options:

```bash
# Option A (Linux, as per the challenge document)
docker run -d --network="host" cfontes0estapar/garage-sim:1.0.0
# app must listen on :3003; simulator API is on http://localhost:3000

# Option B (any OS; verified live on Windows during this discovery)
docker run -d -p 3000:3000 \
  -e CLIENT_WEBHOOK_URL=http://host.docker.internal:3003/webhook \
  cfontes0estapar/garage-sim:1.0.0
```

The repository's `compose.yml` also ships an optional `simulator` profile that wires the
simulator to the app **inside** the compose network (no host networking needed at all).

## 3. Contract decisions locked by this analysis

| Topic | Decision | Where |
|---|---|---|
| Simulator base URL | configurable `garage.simulator.base-url`, default `http://localhost:3000` | `application.yml` |
| App port | `3003` (webhook contract) | `application.yml` |
| `/garage` field names | snake_case primary + `basePrice` alias | simulator client DTO |
| Event timestamps | accept offset and zoneless ISO-8601; zoneless = UTC | Jackson config, ADR-005 |
| `GET /revenue` | query parameters (`?date=&sector=`), not GET-with-body | ADR in README §API; the document's GET-with-body is not canonical REST and is unsupported by many HTTP stacks |
| Idempotency | payload fingerprint + unique constraint | ADR-004 |
| ENTRY without sector | global capacity at ENTRY; sector policy frozen at PARKED | ADR-002 |
