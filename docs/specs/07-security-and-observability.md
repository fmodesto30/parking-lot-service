# 07 — Security & Observability (proportional)

## Security posture

The simulator provides no credentials, so authn/authz on the webhook would break the contract.
Security effort goes to input hardening and secret hygiene; production evolutions are
documented, not implemented (README §Security).

Implemented:

- Strict input validation: closed `event_type` enum; conditional per-event field rules;
  plate `[A-Z0-9-]{1,16}` after trim/uppercase; coordinate range checks; payload size cap.
- DB credentials only via environment (`.env.example` committed, `.env` git-ignored; no secret
  in git — CI uses throwaway Testcontainers credentials).
- Error responses: ProblemDetail without internals; `server.error.include-stacktrace: never`.
- Actuator: only `health`, `info`, `metrics` exposed; no `env`/`beans`/`configprops`/`heapdump`.
- No global CORS opening (no browser consumer exists).
- Correlation id sanitized (length + charset) → log-injection safe.
- Logs never contain full payloads or full plates (masking: `WDE***75`), never secrets.
- Least-privilege MySQL user for the app (not root) in compose.

Documented production evolution (not implemented — out of scope): mTLS or HMAC signature on the
webhook, OAuth2 client-credentials for the query API, network policies/rate limiting/WAF,
secrets manager.

## Logging

Structured single-line logs with stable event names + key-value parameters (no string
concatenation, parameterized SLF4J only):

```
garage_configuration_synchronized sectors=2 spots=30 elapsedMs=412
webhook_event_received type=ENTRY plate=WDE***75
webhook_event_processed type=ENTRY sessionId=42 outcome=PROCESSED
webhook_event_duplicate type=EXIT fingerprint=ab12…
webhook_event_rejected type=PARKED reason=SPOT_OCCUPIED
parking_session_created sessionId=42
vehicle_parked sessionId=42 sector=A spot=10 multiplier=0.90
parking_session_completed sessionId=42 sector=A billableHours=2
parking_charge_created sessionId=42 amount=81.00
garage_full / sector_full sector=A
```

Levels: INFO normal flow; WARN business rejections/duplicates/full; ERROR unexpected technical
failures only. MDC carries `correlationId` on every line.

## Metrics (Micrometer, tag cardinality bounded)

```
garage_webhook_events_total{type,result}         counter
garage_webhook_rejections_total{type,reason}     counter
garage_event_processing_duration{type}           timer
garage_active_sessions                           gauge (from garage_state)
garage_completed_sessions_total                  counter
garage_configuration_sync_failures_total         counter
garage_duplicate_events_total{type}              counter
garage_concurrency_conflicts_total{resource}     counter
```

Tags never include plate, session id, event id or coordinates (unbounded cardinality).
No Prometheus/Grafana/Jaeger shipped — `/actuator/metrics` is the proportional surface.

## Health

- Liveness: process health only — UP as soon as the context is alive.
- Readiness: DOWN until garage configuration is synchronized (custom `HealthIndicator` backed
  by the sync state); flips UP after the first successful sync.
