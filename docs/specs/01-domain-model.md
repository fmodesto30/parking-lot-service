# 01 — Domain Model

## Bounded modules (modular monolith)

| Module | Responsibility |
|---|---|
| `garageconfiguration` | Synchronize and store sectors/spots from the simulator; readiness gating |
| `parking` | Vehicle lifecycle: sessions, spot occupation, capacity control |
| `pricing` | Occupancy-based price policy + time-based billing calculation |
| `revenue` | Immutable charges and revenue queries |
| `webhook` | Transport: event DTO, validation, dispatch to use cases, idempotency registry |
| `shared` | Value objects, error model, correlation id, configuration |

## Aggregates & entities

### ParkingSession (aggregate root — the core of the model)

A vehicle's stay from gate entry to gate exit.

```text
ParkingSession
  id                     : Long (surrogate)
  licensePlate           : LicensePlate (VO)
  status                 : ENTERED | PARKED | EXITED
  entryTime              : Instant        (from ENTRY event)
  parkedAt               : Instant?       (application clock; PARKED event has no timestamp)
  exitTime               : Instant?       (from EXIT event)
  spotId                 : Long?          (set at PARKED)
  sectorCode             : String?        (set at PARKED)
  basePriceSnapshot      : Money?         (frozen at PARKED)
  occupancyRateSnapshot  : BigDecimal?    (frozen at PARKED, 0..1 scale 4)
  priceMultiplierSnapshot: BigDecimal?    (frozen at PARKED)
  effectiveHourlyPrice   : Money?         (frozen at PARKED)
  version                : long (optimistic backstop; pessimistic locks are primary)
```

State machine (behavior methods, never setters):

```text
ENTRY  → new session ENTERED           ParkingSession.enter(plate, entryTime)
ENTERED → PARKED                       session.parkAt(spot, sector, appliedPrice, parkedAt)
PARKED  → EXITED                       session.exitAt(exitTime)   → produces billable data
ENTERED → EXITED                       session.exitAt(exitTime)   → vehicle left without parking; no charge
EXITED  → *                            InvalidSessionTransitionException
```

### Sector (reference data)

`code`, `basePrice: Money`, `maxCapacity: int`. Owned by `garageconfiguration`, read by `parking`/`pricing`.

### ParkingSpot

`externalId` (simulator id), `sectorCode`, `coordinates: Coordinates`, `status: AVAILABLE|OCCUPIED`,
`occupiedBySessionId?`. Occupation transitions only through `occupy(sessionId)` / `release()`.

### GarageState (singleton row)

Global gate capacity: `totalCapacity`, `activeVehicleCount`. The single gate group means ENTRY
admission is a *global* decision (sectors are logical). Reconciled against active sessions at startup.

### ParkingCharge (immutable)

One per completed **parked** stay: `sessionId (unique)`, `sectorCode`, `amount: Money`, `chargedAt: Instant`
(= exit time). Never updated, never deleted. Revenue derives exclusively from this table.

### ProcessedWebhookEvent (idempotency registry)

`fingerprint (unique)`, `eventType`, `licensePlate`, `receivedAt`, `processedAt`, `sessionId?`.

## Value objects

| VO | Invariants |
|---|---|
| `LicensePlate` | non-blank, trimmed, uppercased, `[A-Z0-9-]{1,16}` |
| `Coordinates` | lat ∈ [−90,90], lng ∈ [−180,180]; normalized to scale 6 (simulator precision) |
| `Money` | `BigDecimal` amount scale 2 HALF_UP, currency fixed BRL, never negative |
| `OccupancyRate` | ratio occupied/capacity ∈ [0,1+], scale 4 |
| `AppliedPrice` | base `Money` + multiplier + occupancy + effective hourly `Money` — the frozen pricing decision |

## Invariants (enforced in domain + schema)

1. A plate has at most one **active** session (`ENTERED`/`PARKED`) — domain check + `active_plate` unique column.
2. A spot holds at most one vehicle — pessimistic lock + `occupied_by_session_id` discipline.
3. A session occupies at most one spot; `EXITED` sessions are frozen.
4. `exitTime ≥ entryTime`, else `InvalidExitTimeException`.
5. At most one charge per session — unique constraint on `parking_charge.session_id`.
6. `Money` is non-negative BRL; all arithmetic in `BigDecimal`.
7. `PARKED` requires an existing `ENTERED` session; sector/spot must exist in configuration.
8. Time comes from an injected `Clock` — domain/application never call `Instant.now()` directly.
