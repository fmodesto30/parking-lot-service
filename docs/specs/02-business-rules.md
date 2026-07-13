# 02 — Business Rules

## R1 — Admission (ENTRY)

- ENTRY represents the vehicle passing the single gate group. No sector is known yet (see ADR-002).
- Admission is checked against **global capacity**: `activeVehicleCount < totalCapacity`
  where active = sessions `ENTERED` + `PARKED`.
- Full garage → reject with `GarageFullException` (HTTP 409). No session is created.
- A plate with an active session cannot enter again → `ActiveSessionAlreadyExistsException` (HTTP 409).
- Check + increment happen under a pessimistic lock on the `garage_state` row.

## R2 — Parking (PARKED)

- Spot is resolved by exact coordinates (lat/lng normalized to scale 6). Unknown coordinates →
  `ParkingSpotNotFoundException` (HTTP 404).
- Requires an active `ENTERED` session for the plate → otherwise `ActiveSessionNotFoundException` (404).
- Spot must be `AVAILABLE` → otherwise `ParkingSpotOccupiedException` (409).
- **Sector gating:** occupancy is computed *before* occupying the new spot:
  `occupancy = occupiedSpotsInSector / sectorMaxCapacity`.
  If `occupiedSpotsInSector ≥ maxCapacity` the sector is closed → `SectorFullException` (409).
- Pricing policy is applied and **frozen** on the session at this moment (R3).
- Spot becomes `OCCUPIED`, session becomes `PARKED`.

## R3 — Dynamic pricing (frozen at PARKED)

Multiplier bands over sector occupancy **before** the new vehicle takes its spot:

```text
 0% ≤ occupancy < 25%  → ×0.90   (10% discount)
25% ≤ occupancy < 50%  → ×1.00
50% ≤ occupancy < 75%  → ×1.10
75% ≤ occupancy < 100% → ×1.25
occupancy ≥ 100%       → sector closed (SectorFullException)
```

- `effectiveHourlyPrice = sectorBasePrice × multiplier`, `BigDecimal`, scale 2, HALF_UP.
- Snapshots persisted on the session: base price, occupancy (scale 4), multiplier, effective price.
- The exit calculation **never** re-reads current occupancy or sector price (R4 uses snapshots).

Worked example (sector A, base R$ 40.50, capacity 10):

| Occupied before | Occupancy | Multiplier | Effective/hour |
|---|---|---|---|
| 0 | 0% | 0.90 | 36.45 |
| 2 | 20% | 0.90 | 36.45 |
| 3 | 30% | 1.00 | 40.50 |
| 5 | 50% | 1.10 | 44.55 |
| 8 | 80% | 1.25 | 50.63 |
| 10 | 100% | — | sector closed |

## R4 — Billing (EXIT)

- `duration = exitTime − entryTime` (event-embedded times; second precision; see discovery §2.3).
- `duration ≤ 30min` → **R$ 0.00** (grace period; boundary inclusive: exactly 30:00 is free).
- `duration > 30min` → `billableHours = ceil(duration / 1h)` — the **whole** duration counts,
  not just the excess: 30m01s → 1h; 60m01s → 2h; 119m59s → 2h; 120m01s → 3h.
- `amount = billableHours × effectiveHourlyPrice` (the frozen snapshot).
- One immutable `ParkingCharge` per session (`chargedAt = exitTime`); duplicated EXIT never
  produces a second charge (unique `session_id` + idempotency).
- `PARKED → EXITED`: spot released, global count decremented, charge created (0.00 counts as a
  charge — a completed parked stay always leaves an auditable charge row).
- `ENTERED → EXITED` (never parked): session closes, global count decremented, **no charge**
  (no sector/price context exists; documented deviation, see ADR-002 §consequences).
- `exitTime < entryTime` → `InvalidExitTimeException` (422 semantics carried as 400-family;
  concretely HTTP 400).

## R5 — Revenue

- `GET /revenue?date=YYYY-MM-DD&sector=X` → sum of `parking_charge.amount` for charges whose
  `chargedAt` falls on `date` **in the business timezone** (`America/Sao_Paulo`, configurable).
- Implementation: `[date 00:00, date+1 00:00)` in business zone converted to UTC instants,
  then range query over `charged_at` (stored UTC). See ADR-005.
- No charges → `amount = 0.00`. Unknown sector → 404. Malformed date → 400.
- `currency` fixed `"BRL"`; `timestamp` = query execution time.

## R6 — Configuration synchronization

- On startup fetch `GET /garage`; persist sectors and spots idempotently (upsert by natural key).
- Simulator only starts emitting events **after** `/garage` is called (discovery §2.1) — sync
  must complete before the app is `READY`; webhook returns 503 (`GarageNotReadyException`)
  until then.
- Re-sync (restart) must not duplicate rows, must not overwrite occupation state of occupied
  spots, and must reconcile `garage_state.active_vehicle_count` with actual active sessions.
- Retries: limited attempts with small exponential backoff; then the app stays up but NOT ready
  (liveness UP, readiness DOWN) — no crash loop.
