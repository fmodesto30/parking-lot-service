package com.estapar.garage.pricing.domain;

import com.estapar.garage.shared.domain.Money;
import java.math.BigDecimal;

/**
 * The pricing decision frozen at the moment a vehicle takes a spot: what the base was, how
 * full the sector was, which multiplier applied and the resulting hourly price. Persisted on
 * the session so every charge stays explainable and immune to later occupancy changes.
 */
public record AppliedPrice(
        Money basePrice, OccupancyRate occupancyRate, BigDecimal multiplier, Money effectiveHourlyPrice) {

    public AppliedPrice {
        if (basePrice == null || occupancyRate == null || multiplier == null || effectiveHourlyPrice == null) {
            throw new IllegalArgumentException("applied price requires all components");
        }
    }
}
