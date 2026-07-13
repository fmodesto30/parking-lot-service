package com.estapar.garage.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Geographic pair normalized to scale 6 — the simulator's precision (see
 * docs/specs/00-challenge-analysis.md). Normalization makes coordinate equality and the
 * PARKED fingerprint deterministic regardless of how many trailing zeros the sender used.
 */
public record Coordinates(BigDecimal latitude, BigDecimal longitude) {

    private static final BigDecimal LAT_LIMIT = BigDecimal.valueOf(90);
    private static final BigDecimal LNG_LIMIT = BigDecimal.valueOf(180);

    public Coordinates {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("coordinates must not be null");
        }
        if (latitude.abs().compareTo(LAT_LIMIT) > 0) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude.abs().compareTo(LNG_LIMIT) > 0) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
        latitude = latitude.setScale(6, RoundingMode.HALF_UP);
        longitude = longitude.setScale(6, RoundingMode.HALF_UP);
    }
}
