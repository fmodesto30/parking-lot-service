CREATE TABLE parking_spot (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    external_id            BIGINT       NOT NULL,
    sector_code            VARCHAR(16)  NOT NULL,
    latitude               DECIMAL(9,6) NOT NULL,
    longitude              DECIMAL(9,6) NOT NULL,
    status                 VARCHAR(16)  NOT NULL,
    occupied_by_session_id BIGINT       NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_parking_spot_external_id UNIQUE (external_id),
    CONSTRAINT uq_parking_spot_coordinates UNIQUE (latitude, longitude),
    CONSTRAINT fk_parking_spot_sector FOREIGN KEY (sector_code) REFERENCES sector (code),
    CONSTRAINT chk_parking_spot_status CHECK (status IN ('AVAILABLE', 'OCCUPIED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
