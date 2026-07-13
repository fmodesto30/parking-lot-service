package com.estapar.garage.pricing.domain;

import com.estapar.garage.shared.domain.Money;
import java.time.Duration;

/**
 * Time-based billing (spec 02 R4): stays up to 30 minutes are free; beyond that, the
 * <em>whole</em> duration is billed in hours rounded up (30m01s → 1h, 60m01s → 2h).
 * Second-level precision; sub-hour remainders always round to the next full hour.
 */
public final class BillingCalculator {

    private static final Duration GRACE_PERIOD = Duration.ofMinutes(30);
    private static final long SECONDS_PER_HOUR = 3600;

    public Money charge(Duration stay, Money effectiveHourlyPrice) {
        if (stay.isNegative()) {
            throw new IllegalArgumentException("stay duration must not be negative: " + stay);
        }
        return effectiveHourlyPrice.times(billableHours(stay));
    }

    public long billableHours(Duration stay) {
        if (stay.compareTo(GRACE_PERIOD) <= 0) {
            return 0;
        }
        long fullSeconds = stay.getSeconds();
        long hours = fullSeconds / SECONDS_PER_HOUR;
        boolean hasRemainder = fullSeconds % SECONDS_PER_HOUR > 0 || stay.getNano() > 0;
        return hasRemainder ? hours + 1 : hours;
    }
}
