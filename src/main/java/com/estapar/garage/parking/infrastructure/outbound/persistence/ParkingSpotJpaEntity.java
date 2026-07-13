package com.estapar.garage.parking.infrastructure.outbound.persistence;

import com.estapar.garage.parking.domain.SpotStatus;
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
@Table(name = "parking_spot")
public class ParkingSpotJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false)
    private long externalId;

    @Column(name = "sector_code", nullable = false, length = 16)
    private String sectorCode;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SpotStatus status;

    @Column(name = "occupied_by_session_id")
    private Long occupiedBySessionId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ParkingSpotJpaEntity() {}

    public ParkingSpotJpaEntity(
            long externalId,
            String sectorCode,
            BigDecimal latitude,
            BigDecimal longitude,
            SpotStatus status,
            Long occupiedBySessionId) {
        this.externalId = externalId;
        this.sectorCode = sectorCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
        this.occupiedBySessionId = occupiedBySessionId;
    }

    public Long getId() {
        return id;
    }

    public long getExternalId() {
        return externalId;
    }

    public String getSectorCode() {
        return sectorCode;
    }

    public void setSectorCode(String sectorCode) {
        this.sectorCode = sectorCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public SpotStatus getStatus() {
        return status;
    }

    public void setStatus(SpotStatus status) {
        this.status = status;
    }

    public Long getOccupiedBySessionId() {
        return occupiedBySessionId;
    }

    public void setOccupiedBySessionId(Long occupiedBySessionId) {
        this.occupiedBySessionId = occupiedBySessionId;
    }
}
