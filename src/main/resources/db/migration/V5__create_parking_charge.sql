-- Immutable: the application only ever INSERTs into this table.
CREATE TABLE parking_charge (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    session_id  BIGINT        NOT NULL,
    sector_code VARCHAR(16)   NOT NULL,
    amount      DECIMAL(10,2) NOT NULL,
    currency    VARCHAR(3)    NOT NULL DEFAULT 'BRL',
    charged_at  DATETIME(6)   NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_parking_charge_session UNIQUE (session_id),
    CONSTRAINT fk_parking_charge_session FOREIGN KEY (session_id) REFERENCES parking_session (id),
    CONSTRAINT chk_parking_charge_amount_non_negative CHECK (amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
