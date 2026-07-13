# ADR-001 — Modular Monolith

**Status:** accepted · **Date:** 2026-07-13

## Context

The system manages one garage: configuration sync, three webhook events, one query endpoint,
one MySQL database. Traffic is a single simulator emitting events every few seconds. The
strongest consistency requirements (capacity, spot occupation, single charge) live *between*
the would-be service boundaries.

## Decision

One Spring Boot application, one database, organized as a **modular monolith**:
`garageconfiguration`, `parking`, `pricing`, `revenue`, `webhook`, `shared` — each module with
`domain` / `application` / `infrastructure` layers, dependencies pointing inward, boundaries
enforced by ArchUnit.

## Rationale

- The invariants (global capacity + spot + charge in one EXIT transaction) are trivially
  correct inside one ACID database. Splitting services would force distributed transactions or
  sagas for a problem a single `@Transactional` method solves.
- Microservices, Kafka, Redis, CQRS et al. would add operational surface with zero functional
  gain at this scale — the evaluation explicitly values proportionality.
- Module boundaries keep the code navigable and make a *future* decomposition mechanical:
  each module already owns its tables and exposes intent-named use cases; `pricing` is a pure
  library candidate; `revenue` is a natural read-model seam.

## Consequences

- Single deployable, single pipeline, compose with two containers (app + MySQL).
- Horizontal scaling remains possible: all mutual exclusion lives in MySQL locks, not JVM state.
- ArchUnit tests fail the build if a controller touches a repository, the domain imports
  Spring/JPA, or modules bypass each other's application layer.
