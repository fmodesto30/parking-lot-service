package com.estapar.garage.parking.infrastructure.outbound.persistence;

import com.estapar.garage.parking.domain.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "parking_session")
public class ParkingSessionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_plate", nullable = false, length = 16)
    private String licensePlate;

    /** Mirrors the plate while active; NULL after exit — unique index enforces one active session per plate. */
    @Column(name = "active_plate", length = 16)
    private String activePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SessionStatus status;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "parked_at")
    private Instant parkedAt;

    @Column(name = "exit_time")
    private Instant exitTime;

    @Column(name = "spot_id")
    private Long spotId;

    @Column(name = "sector_code", length = 16)
    private String sectorCode;

    @Column(name = "base_price_snapshot", precision = 10, scale = 2)
    private BigDecimal basePriceSnapshot;

    @Column(name = "occupancy_rate_snapshot", precision = 5, scale = 4)
    private BigDecimal occupancyRateSnapshot;

    @Column(name = "price_multiplier_snapshot", precision = 4, scale = 2)
    private BigDecimal priceMultiplierSnapshot;

    @Column(name = "effective_hourly_price", precision = 10, scale = 2)
    private BigDecimal effectiveHourlyPrice;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ParkingSessionJpaEntity() {}

    public ParkingSessionJpaEntity(String licensePlate, String activePlate, SessionStatus status, Instant entryTime) {
        this.licensePlate = licensePlate;
        this.activePlate = activePlate;
        this.status = status;
        this.entryTime = entryTime;
    }

    public Long getId() {
        return id;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getActivePlate() {
        return activePlate;
    }

    public void setActivePlate(String activePlate) {
        this.activePlate = activePlate;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public Instant getEntryTime() {
        return entryTime;
    }

    public Instant getParkedAt() {
        return parkedAt;
    }

    public void setParkedAt(Instant parkedAt) {
        this.parkedAt = parkedAt;
    }

    public Instant getExitTime() {
        return exitTime;
    }

    public void setExitTime(Instant exitTime) {
        this.exitTime = exitTime;
    }

    public Long getSpotId() {
        return spotId;
    }

    public void setSpotId(Long spotId) {
        this.spotId = spotId;
    }

    public String getSectorCode() {
        return sectorCode;
    }

    public void setSectorCode(String sectorCode) {
        this.sectorCode = sectorCode;
    }

    public BigDecimal getBasePriceSnapshot() {
        return basePriceSnapshot;
    }

    public void setBasePriceSnapshot(BigDecimal basePriceSnapshot) {
        this.basePriceSnapshot = basePriceSnapshot;
    }

    public BigDecimal getOccupancyRateSnapshot() {
        return occupancyRateSnapshot;
    }

    public void setOccupancyRateSnapshot(BigDecimal occupancyRateSnapshot) {
        this.occupancyRateSnapshot = occupancyRateSnapshot;
    }

    public BigDecimal getPriceMultiplierSnapshot() {
        return priceMultiplierSnapshot;
    }

    public void setPriceMultiplierSnapshot(BigDecimal priceMultiplierSnapshot) {
        this.priceMultiplierSnapshot = priceMultiplierSnapshot;
    }

    public BigDecimal getEffectiveHourlyPrice() {
        return effectiveHourlyPrice;
    }

    public void setEffectiveHourlyPrice(BigDecimal effectiveHourlyPrice) {
        this.effectiveHourlyPrice = effectiveHourlyPrice;
    }
}
