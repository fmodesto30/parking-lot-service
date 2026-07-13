CREATE TABLE garage_state (
    id                   BIGINT      NOT NULL,
    total_capacity       INT         NOT NULL,
    active_vehicle_count INT         NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0,
    last_synchronized_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_garage_state_capacity_non_negative CHECK (total_capacity >= 0),
    CONSTRAINT chk_garage_state_count_non_negative    CHECK (active_vehicle_count >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Single control row; capacity is populated by the first configuration sync.
INSERT INTO garage_state (id, total_capacity, active_vehicle_count, version, last_synchronized_at)
VALUES (1, 0, 0, 0, NULL);
