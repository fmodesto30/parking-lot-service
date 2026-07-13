package com.estapar.garage.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Sector occupancy as the exact fraction occupied/capacity. Band decisions use integer
 * arithmetic on the raw pair — no rounding can ever push a value across a band boundary.
 * {@link #value()} exposes the scale-4 decimal used for persistence/reporting only.
 */
public record OccupancyRate(long occupied, long capacity) {

    public OccupancyRate {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        if (occupied < 0) {
            throw new IllegalArgumentException("occupied must not be negative: " + occupied);
        }
    }

    /** occupied/capacity < percent/100, computed exactly in integer space. */
    public boolean isBelowPercent(int percent) {
        return occupied * 100 < capacity * (long) percent;
    }

    public boolean isFull() {
        return occupied >= capacity;
    }

    /** Decimal form (scale 4, HALF_UP) for snapshots and logs. */
    public BigDecimal value() {
        return BigDecimal.valueOf(occupied).divide(BigDecimal.valueOf(capacity), 4, RoundingMode.HALF_UP);
    }
}
