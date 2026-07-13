package com.estapar.garage.pricing.domain;

import com.estapar.garage.shared.domain.Money;
import java.math.BigDecimal;

/**
 * The pricing decision frozen at the moment a vehicle takes a spot: what the base was, how
 * full the sector was (scale-4 decimal snapshot), which multiplier applied and the resulting
 * hourly price. Persisted on the session so every charge stays explainable and immune to
 * later occupancy changes. Band decisions never use this decimal — see {@link OccupancyRate}.
 */
public record AppliedPrice(
        Money basePrice, BigDecimal occupancyRate, BigDecimal multiplier, Money effectiveHourlyPrice) {

    public AppliedPrice {
        if (basePrice == null || occupancyRate == null || multiplier == null || effectiveHourlyPrice == null) {
            throw new IllegalArgumentException("applied price requires all components");
        }
    }
}
