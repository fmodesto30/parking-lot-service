package com.estapar.garage.pricing.domain;

import com.estapar.garage.shared.domain.Money;

/**
 * Dynamic pricing rule applied when a vehicle is about to occupy a spot. Exists as a port so
 * the use case depends on the rule's intent rather than its arithmetic, and tests can probe
 * the boundary table in isolation. Single production implementation by design — see
 * {@link TieredOccupancyPricingPolicy}.
 */
public interface OccupancyPricingPolicy {

    /**
     * Calculates the frozen price for the occupancy observed <em>before</em> the new vehicle
     * takes its spot.
     *
     * @throws IllegalArgumentException if the sector is already full — callers gate capacity
     *     with a proper domain rejection before pricing
     */
    AppliedPrice calculate(Money basePrice, long occupiedSpots, long maximumCapacity);
}
