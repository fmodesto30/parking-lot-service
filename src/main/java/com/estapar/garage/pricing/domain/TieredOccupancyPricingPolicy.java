package com.estapar.garage.pricing.domain;

import com.estapar.garage.shared.domain.Money;
import java.math.BigDecimal;

/**
 * Challenge rule (spec 02 R3), occupancy measured before the new vehicle parks:
 *
 * <pre>
 *  0% ≤ occupancy &lt; 25%  → ×0.90
 * 25% ≤ occupancy &lt; 50%  → ×1.00
 * 50% ≤ occupancy &lt; 75%  → ×1.10
 * 75% ≤ occupancy &lt; 100% → ×1.25
 * </pre>
 */
public final class TieredOccupancyPricingPolicy implements OccupancyPricingPolicy {

    private static final BigDecimal LOW_OCCUPANCY_DISCOUNT = new BigDecimal("0.90");
    private static final BigDecimal REGULAR_PRICE = new BigDecimal("1.00");
    private static final BigDecimal HIGH_OCCUPANCY_SURCHARGE = new BigDecimal("1.10");
    private static final BigDecimal PEAK_OCCUPANCY_SURCHARGE = new BigDecimal("1.25");

    @Override
    public AppliedPrice calculate(Money basePrice, long occupiedSpots, long maximumCapacity) {
        var occupancy = new OccupancyRate(occupiedSpots, maximumCapacity);
        if (occupancy.isFull()) {
            throw new IllegalArgumentException("sector is full; pricing requires available capacity");
        }
        var multiplier = multiplierFor(occupancy);
        return new AppliedPrice(basePrice, occupancy.value(), multiplier, basePrice.multiplyBy(multiplier));
    }

    private BigDecimal multiplierFor(OccupancyRate occupancy) {
        if (occupancy.isBelowPercent(25)) {
            return LOW_OCCUPANCY_DISCOUNT;
        }
        if (occupancy.isBelowPercent(50)) {
            return REGULAR_PRICE;
        }
        if (occupancy.isBelowPercent(75)) {
            return HIGH_OCCUPANCY_SURCHARGE;
        }
        return PEAK_OCCUPANCY_SURCHARGE;
    }
}
