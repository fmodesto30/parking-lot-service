package com.estapar.garage.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary amount in BRL, normalized to scale 2 (HALF_UP). Never negative in this domain:
 * charges are zero or positive, and prices cannot go below zero.
 */
public record Money(BigDecimal amount) {

    public static final String CURRENCY = "BRL";
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public Money multiplyBy(BigDecimal factor) {
        return new Money(amount.multiply(factor));
    }

    public Money times(long quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    public String currency() {
        return CURRENCY;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }
}
