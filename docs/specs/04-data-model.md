# 04 — Data Model (MySQL 8, Flyway-managed)

`spring.jpa.hibernate.ddl-auto=validate` — Flyway is the only source of schema truth.
All timestamps stored as UTC (`TIMESTAMP(6)`/`DATETIME(6)`); business-date queries convert at
the edges (ADR-005). Coordinates use `DECIMAL(9,6)` — never floating point.

## Tables

### sector (V1)
```sql
code            VARCHAR(16)  PK
base_price      DECIMAL(10,2) NOT NULL CHECK (base_price >= 0)
max_capacity    INT NOT NULL CHECK (max_capacity > 0)
created_at / updated_at DATETIME(6)
```

### parking_spot (V2)
```sql
id              BIGINT PK AUTO_INCREMENT
external_id     BIGINT NOT NULL UNIQUE            -- simulator's spot id
sector_code     VARCHAR(16) NOT NULL FK→sector
latitude        DECIMAL(9,6) NOT NULL
longitude       DECIMAL(9,6) NOT NULL
status          VARCHAR(16) NOT NULL              -- AVAILABLE | OCCUPIED
occupied_by_session_id BIGINT NULL
version         BIGINT NOT NULL DEFAULT 0
created_at / updated_at
UNIQUE (latitude, longitude)
INDEX (sector_code, status)
```

### garage_state (V3) — single row (id=1), global gate capacity
```sql
id                    BIGINT PK
total_capacity        INT NOT NULL
active_vehicle_count  INT NOT NULL CHECK (active_vehicle_count >= 0)
version               BIGINT NOT NULL DEFAULT 0
last_synchronized_at  DATETIME(6) NULL
```

### parking_session (V4)
```sql
id              BIGINT PK AUTO_INCREMENT
license_plate   VARCHAR(16) NOT NULL
active_plate    VARCHAR(16) NULL        -- = license_plate while status != EXITED, else NULL
status          VARCHAR(16) NOT NULL    -- ENTERED | PARKED | EXITED
entry_time      DATETIME(6) NOT NULL
parked_at       DATETIME(6) NULL
exit_time       DATETIME(6) NULL
spot_id         BIGINT NULL FK→parking_spot
sector_code     VARCHAR(16) NULL
base_price_snapshot       DECIMAL(10,2) NULL
occupancy_rate_snapshot   DECIMAL(5,4)  NULL
price_multiplier_snapshot DECIMAL(4,2)  NULL
effective_hourly_price    DECIMAL(10,2) NULL
version         BIGINT NOT NULL DEFAULT 0
created_at / updated_at
UNIQUE (active_plate)                    -- MySQL: multiple NULLs allowed → exactly one ACTIVE session per plate
INDEX (license_plate, status)
INDEX (status)
```

> `active_plate` is the MySQL idiom for a partial unique index (which MySQL lacks): the column
> mirrors the plate while the session is active and is set to `NULL` on exit; UNIQUE ignores
> NULLs, so history is unlimited but concurrent active duplicates are impossible at the
> constraint level — the last line of defense under races.

### parking_charge (V5) — immutable
```sql
id           BIGINT PK AUTO_INCREMENT
session_id   BIGINT NOT NULL UNIQUE FK→parking_session
sector_code  VARCHAR(16) NOT NULL
amount       DECIMAL(10,2) NOT NULL CHECK (amount >= 0)
currency     CHAR(3) NOT NULL DEFAULT 'BRL'
charged_at   DATETIME(6) NOT NULL      -- = exit time (business event instant)
created_at   DATETIME(6)
INDEX (sector_code, charged_at)
```
No UPDATE/DELETE path exists in the application for this table.

### processed_webhook_event (V6)
```sql
id            BIGINT PK AUTO_INCREMENT
fingerprint   CHAR(64) NOT NULL UNIQUE  -- SHA-256 hex
event_type    VARCHAR(16) NOT NULL
license_plate VARCHAR(16) NOT NULL
received_at   DATETIME(6) NOT NULL
processed_at  DATETIME(6) NOT NULL
session_id    BIGINT NULL
```

### V7 — cross-cutting constraints & remaining indexes
FKs declared with the owning tables; V7 adds any late composite indexes and documents the
locking-related access paths (`SELECT ... FOR UPDATE` targets: `garage_state` by PK,
`parking_spot` by unique coords, `parking_session` by `active_plate`).

## Migration files

```
V1__create_sector.sql
V2__create_parking_spot.sql
V3__create_garage_state.sql
V4__create_parking_session.sql
V5__create_parking_charge.sql
V6__create_processed_webhook_event.sql
V7__add_constraints_and_indexes.sql
```
