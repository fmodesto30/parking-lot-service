package com.estapar.garage.parking.infrastructure.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "garage_state")
public class GarageStateJpaEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "active_vehicle_count", nullable = false)
    private int activeVehicleCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "last_synchronized_at")
    private Instant lastSynchronizedAt;

    protected GarageStateJpaEntity() {}

    public Long getId() {
        return id;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(int totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    public int getActiveVehicleCount() {
        return activeVehicleCount;
    }

    public void setActiveVehicleCount(int activeVehicleCount) {
        this.activeVehicleCount = activeVehicleCount;
    }

    public Instant getLastSynchronizedAt() {
        return lastSynchronizedAt;
    }

    public void setLastSynchronizedAt(Instant lastSynchronizedAt) {
        this.lastSynchronizedAt = lastSynchronizedAt;
    }
}
