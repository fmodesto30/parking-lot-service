package com.estapar.garage.shared.domain;

import java.util.regex.Pattern;

/** Vehicle license plate, normalized (trimmed, uppercased) and validated at the boundary. */
public record LicensePlate(String value) {

    private static final Pattern VALID = Pattern.compile("[A-Z0-9-]{1,16}");

    public LicensePlate {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("license plate must not be blank");
        }
        value = value.trim().toUpperCase();
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("license plate has invalid format");
        }
    }

    /** Log-safe form: enough to correlate, not enough to identify (LGPD-friendly). */
    public String masked() {
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 2);
    }
}
