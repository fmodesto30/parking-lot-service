# 03 — API Contract

Served on port **3003** (webhook contract from the challenge document).

## POST /webhook

Consumes the simulator's events. Transport DTO mirrors the real payloads (snake_case).

### ENTRY
```json
{ "license_plate": "ZUL0001", "entry_time": "2025-01-01T12:00:00.000Z", "event_type": "ENTRY" }
```
Also accepted (real simulator format): `"entry_time": "2026-07-13T16:02:11"` → interpreted as UTC.

### PARKED
```json
{ "license_plate": "ZUL0001", "lat": -23.561684, "lng": -46.655981, "event_type": "PARKED" }
```

### EXIT
```json
{ "license_plate": "ZUL0001", "exit_time": "2025-01-01T12:00:00.000Z", "event_type": "EXIT" }
```

### Validation (conditional per event type)

| Field | ENTRY | PARKED | EXIT |
|---|---|---|---|
| `license_plate` | required | required | required |
| `entry_time` | **required** | forbidden* | forbidden* |
| `exit_time` | forbidden* | forbidden* | **required** |
| `lat`, `lng` | forbidden* | **required** | forbidden* |

\* Forbidden = present-and-ambiguous fields are rejected with 400 (`InvalidWebhookEventException`)
to avoid silently accepting contradictory payloads (e.g. ENTRY carrying `exit_time`).

### Responses

| Case | Status | Body |
|---|---|---|
| Processed | **200** | `{ "result": "PROCESSED", ... }` |
| Exact duplicate (same fingerprint) | **200** | `{ "result": "DUPLICATE", ... }` |
| Malformed/invalid payload, unknown `event_type`, invalid times | **400** | ProblemDetail |
| Unknown spot coordinates / no active session / unknown sector | **404** | ProblemDetail |
| Garage full / sector full / spot occupied / active session exists / invalid transition | **409** | ProblemDetail |
| Configuration not yet synchronized | **503** | ProblemDetail |
| Unexpected failure | **500** | ProblemDetail (no stack trace) |

## GET /revenue

The document sketches `GET` with a JSON body; body-on-GET is non-canonical and poorly supported,
so the canonical contract uses **query parameters** (decision recorded in README §API):

```
GET /revenue?date=2025-01-01&sector=A
```

Response `200`:
```json
{ "amount": 25.00, "currency": "BRL", "timestamp": "2025-01-01T15:00:00.000Z" }
```

- `amount`: sum of immutable charges for that sector/date (business timezone), scale 2.
- No revenue → `0.00`. Unknown sector → 404. Invalid/missing date → 400.

## Operational endpoints

- `GET /actuator/health` (+ `/liveness`, `/readiness` probes) — readiness is DOWN until the
  garage configuration is synchronized.
- `GET /actuator/info`, `GET /actuator/metrics` (Micrometer).
- `GET /swagger-ui.html`, `GET /v3/api-docs` (springdoc; disabled surface kept minimal).

## Headers

- `X-Correlation-Id` accepted on all endpoints (≤ 64 chars, `[A-Za-z0-9._-]`); generated UUID
  otherwise; always echoed in the response and present in logs (MDC).

## Error model

RFC-7807 `ProblemDetail` (`application/problem+json`) with `type`, `title`, `status`, `detail`,
`instance`, plus extension `correlationId`. Stack traces never leak.
