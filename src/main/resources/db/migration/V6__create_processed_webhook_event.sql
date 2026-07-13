CREATE TABLE processed_webhook_event (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    fingerprint   CHAR(64)    NOT NULL,
    event_type    VARCHAR(16) NOT NULL,
    license_plate VARCHAR(16) NOT NULL,
    received_at   DATETIME(6) NOT NULL,
    processed_at  DATETIME(6) NOT NULL,
    session_id    BIGINT      NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_processed_webhook_event_fingerprint UNIQUE (fingerprint)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
