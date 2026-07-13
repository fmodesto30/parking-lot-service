# ADR-002 — ENTRY vs PARKED: where the dynamic price is fixed

**Status:** accepted · **Date:** 2026-07-13

## Context

The document demands dynamic pricing "at entry time" based on **sector** occupancy — but the
`ENTRY` event carries no sector and no spot (verified against the real simulator: payload is
plate + entry_time only). The sector becomes knowable only when `PARKED` delivers coordinates.
The document also states the garage has a **single gate group** and sectors are logical
divisions. These two statements cannot both be satisfied literally: at the gate, the sector
price is undeterminable.

## Decision

1. `ENTRY` = physical admission through the gate. It creates the session (`ENTERED`) and
   consumes **global** capacity (sum of sector capacities). No spot, no sector, no price yet.
2. `PARKED` = the first technically possible moment to apply the sector policy. The spot is
   resolved by coordinates, the sector's occupancy is measured **before** occupying the new
   spot, and base price, occupancy, multiplier and effective hourly price are **frozen** on the
   session, immediately before the spot is taken.
3. `EXIT` bills using the frozen snapshot — never re-reading live occupancy.
4. A vehicle may exit without ever parking (`ENTERED → EXITED`): no sector context ever
   existed, so no charge is produced. Duration-based billing without a sector price would
   require inventing a tariff the document doesn't define.

## Alternatives considered

- **Price at ENTRY using global occupancy** — contradicts "sector" pricing explicitly;
  distorts prices between sectors with different tariffs (A=40.50 vs B=4.10).
- **Price at ENTRY using a default sector** — fabricates data; wrong whenever the vehicle
  parks elsewhere.
- **Defer pricing to EXIT** — violates "at entry" even harder and re-prices after the fact.
- **Change the contract** (ENTRY carrying `sector`/`spot_id`) — the honest fix, but the
  simulator's contract is fixed for this test; recorded in README as the recommended
  upstream evolution.

## Consequences

- "At entry" is interpreted as "at the start of the *parked* stay, before occupation" — the
  earliest moment the rule is computable. The deviation is explicit here and in the README.
- Sessions that exit while `ENTERED` are auditable (state history) but revenue-neutral.
- The freeze makes bills reproducible and immune to later occupancy changes, and the snapshot
  columns make every charge explainable (base × multiplier @ occupancy).
