package com.estapar.garage.parking.domain;

import com.estapar.garage.parking.domain.exception.GarageFullException;
import java.time.Instant;

/**
 * Global gate capacity — the single gate group makes admission a global decision (sectors are
 * logical, spec 00 §1). One row; mutated only under a pessimistic lock (ADR-003).
 */
public class GarageState {

    private final long id;
    private int totalCapacity;
    private int activeVehicleCount;
    private Instant lastSynchronizedAt;

    public GarageState(long id, int totalCapacity, int activeVehicleCount, Instant lastSynchronizedAt) {
        if (totalCapacity < 0 || activeVehicleCount < 0) {
            throw new IllegalArgumentException("capacity and count must not be negative");
        }
        this.id = id;
        this.totalCapacity = totalCapacity;
        this.activeVehicleCount = activeVehicleCount;
        this.lastSynchronizedAt = lastSynchronizedAt;
    }

    /** ENTRY admission: rejects when full (also true before the first sync, capacity 0). */
    public void admitVehicle() {
        if (activeVehicleCount >= totalCapacity) {
            throw new GarageFullException(totalCapacity);
        }
        activeVehicleCount++;
    }

    /** EXIT: frees one unit of global capacity. Defensive floor at zero. */
    public void releaseVehicle() {
        if (activeVehicleCount > 0) {
            activeVehicleCount--;
        }
    }

    /** Configuration sync: new capacity + reconciled count derived from actual active sessions. */
    public void synchronize(int totalCapacity, int reconciledActiveCount, Instant when) {
        if (totalCapacity < 0 || reconciledActiveCount < 0) {
            throw new IllegalArgumentException("capacity and count must not be negative");
        }
        this.totalCapacity = totalCapacity;
        this.activeVehicleCount = reconciledActiveCount;
        this.lastSynchronizedAt = when;
    }

    public long id() {
        return id;
    }

    public int totalCapacity() {
        return totalCapacity;
    }

    public int activeVehicleCount() {
        return activeVehicleCount;
    }

    public Instant lastSynchronizedAt() {
        return lastSynchronizedAt;
    }
}
