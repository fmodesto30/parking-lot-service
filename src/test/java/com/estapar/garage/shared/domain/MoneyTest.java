package com.estapar.garage.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesToScaleTwoHalfUp() {
        assertThat(Money.of("50.625").amount()).isEqualByComparingTo("50.63");
        assertThat(Money.of("50.624").amount()).isEqualByComparingTo("50.62");
    }

    @Test
    void currencyIsAlwaysBrl() {
        assertThat(Money.of("1.00").currency()).isEqualTo("BRL");
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("-0.01"));
    }

    @Test
    void shouldRejectNullAmount() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of((BigDecimal) null));
    }

    @Test
    void multipliesKeepingMonetaryScale() {
        assertThat(Money.of("40.50").multiplyBy(new BigDecimal("0.90")).amount())
                .isEqualByComparingTo("36.45");
        assertThat(Money.of("36.45").times(3).amount()).isEqualByComparingTo("109.35");
    }

    @Test
    void zeroIsZero() {
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.ZERO.amount()).isEqualByComparingTo("0.00");
    }
}
