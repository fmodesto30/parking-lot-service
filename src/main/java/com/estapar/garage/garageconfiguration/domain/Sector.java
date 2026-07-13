package com.estapar.garage.garageconfiguration.domain;

import com.estapar.garage.shared.domain.Money;
import java.util.Objects;

/** Logical sector of the garage: tariff base and capacity, as synced from the simulator. */
public record Sector(String code, Money basePrice, int maxCapacity) {

    public Sector {
        Objects.requireNonNull(basePrice, "basePrice");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("sector code must not be blank");
        }
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("sector max capacity must be positive: " + maxCapacity);
        }
    }
}
