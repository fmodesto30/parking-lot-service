CREATE TABLE parking_session (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,
    license_plate             VARCHAR(16)   NOT NULL,
    -- Mirrors license_plate while the session is active (ENTERED/PARKED); NULL once EXITED.
    -- MySQL UNIQUE ignores NULLs, so this enforces "one active session per plate" while
    -- keeping unlimited history (MySQL has no partial indexes).
    active_plate              VARCHAR(16)   NULL,
    status                    VARCHAR(16)   NOT NULL,
    entry_time                DATETIME(6)   NOT NULL,
    parked_at                 DATETIME(6)   NULL,
    exit_time                 DATETIME(6)   NULL,
    spot_id                   BIGINT        NULL,
    sector_code               VARCHAR(16)   NULL,
    base_price_snapshot       DECIMAL(10,2) NULL,
    occupancy_rate_snapshot   DECIMAL(5,4)  NULL,
    price_multiplier_snapshot DECIMAL(4,2)  NULL,
    effective_hourly_price    DECIMAL(10,2) NULL,
    version                   BIGINT        NOT NULL DEFAULT 0,
    created_at                DATETIME(6)   NOT NULL,
    updated_at                DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_parking_session_active_plate UNIQUE (active_plate),
    CONSTRAINT fk_parking_session_spot FOREIGN KEY (spot_id) REFERENCES parking_spot (id),
    CONSTRAINT chk_parking_session_status CHECK (status IN ('ENTERED', 'PARKED', 'EXITED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
