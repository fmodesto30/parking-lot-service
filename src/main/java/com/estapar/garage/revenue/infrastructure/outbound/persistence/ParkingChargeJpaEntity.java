package com.estapar.garage.revenue.infrastructure.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

/** Insert-only by construction: {@link Immutable} makes Hibernate reject any UPDATE. */
@Entity
@Immutable
@Table(name = "parking_charge")
public class ParkingChargeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private long sessionId;

    @Column(name = "sector_code", nullable = false, length = 16)
    private String sectorCode;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "charged_at", nullable = false)
    private Instant chargedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ParkingChargeJpaEntity() {}

    public ParkingChargeJpaEntity(
            long sessionId, String sectorCode, BigDecimal amount, String currency, Instant chargedAt) {
        this.sessionId = sessionId;
        this.sectorCode = sectorCode;
        this.amount = amount;
        this.currency = currency;
        this.chargedAt = chargedAt;
    }

    public Long getId() {
        return id;
    }

    public long getSessionId() {
        return sessionId;
    }

    public String getSectorCode() {
        return sectorCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getChargedAt() {
        return chargedAt;
    }
}
