package com.estapar.garage.revenue.domain;

import com.estapar.garage.shared.domain.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of a completed parked stay's bill. Created exactly once per session
 * (unique constraint) and never updated — revenue derives exclusively from these rows.
 */
public record ParkingCharge(Long id, long sessionId, String sectorCode, Money amount, Instant chargedAt) {

    public ParkingCharge {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(chargedAt, "chargedAt");
        if (sectorCode == null || sectorCode.isBlank()) {
            throw new IllegalArgumentException("sector code must not be blank");
        }
    }

    public static ParkingCharge of(long sessionId, String sectorCode, Money amount, Instant chargedAt) {
        return new ParkingCharge(null, sessionId, sectorCode, amount, chargedAt);
    }
}
