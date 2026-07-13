CREATE TABLE sector (
    code         VARCHAR(16)   NOT NULL,
    base_price   DECIMAL(10,2) NOT NULL,
    max_capacity INT           NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (code),
    CONSTRAINT chk_sector_base_price_non_negative CHECK (base_price >= 0),
    CONSTRAINT chk_sector_max_capacity_positive   CHECK (max_capacity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
