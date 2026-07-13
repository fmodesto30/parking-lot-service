# ADR-005 — Revenue date, timezone and timestamp handling

**Status:** accepted · **Date:** 2026-07-13

## Context

Revenue is queried by calendar date + sector. A calendar date only means something in a
timezone; the garage operates in Brazil, the JVM/container default zone is an accident of the
environment, and the simulator emits **UTC wall-clock timestamps without zone suffix**
(`2026-07-13T16:02:11` — verified against the simulator's own UTC logs; the document's example
uses `...Z`).

## Decision

1. **Storage is UTC.** All instants (`entry_time`, `exit_time`, `charged_at`, audit columns)
   are persisted as UTC `DATETIME(6)`; JPA works with `Instant`, `hibernate.jdbc.time_zone=UTC`.
2. **Inbound parsing is lenient, deterministic:** ISO-8601 with offset/`Z` honored as given;
   zoneless local-date-time interpreted as **UTC** (matches observed simulator behavior).
3. **The revenue date is the business date of the charge**, and `charged_at = exit_time` —
   the instant the payment obligation crystallizes.
4. **Business zone is explicit configuration:** `garage.business-zone`
   (default `America/Sao_Paulo`), never `ZoneId.systemDefault()`.
5. Queries convert `date` → `[00:00, next 00:00)` in the business zone → two UTC instants →
   half-open range scan on `charged_at` (index `(sector_code, charged_at)`), so day boundaries
   are exact across DST-less BRT and would survive zone rule changes.

## Consequences

- An exit at `2025-01-02T02:30Z` (= `2025-01-01T23:30` in São Paulo) counts toward
  **2025-01-01** — asserted by an integration test around midnight.
- Changing the business zone is a config change, not a code change; historical UTC data is
  untouched.
- The API responds with UTC ISO-8601 (`timestamp` field), keeping the wire format
  zone-explicit end to end.
