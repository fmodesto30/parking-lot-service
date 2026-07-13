package com.estapar.garage.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class OccupancyRateTest {

    @Test
    void comparesBandsInExactIntegerSpace() {
        // 24996/100000 = 24.996% — a scale-4 HALF_UP decimal would round to 0.2500 and
        // wrongly cross the 25% boundary; the integer comparison must not.
        var rate = new OccupancyRate(24_996, 100_000);

        assertThat(rate.isBelowPercent(25)).isTrue();
        assertThat(rate.value()).isEqualByComparingTo("0.2500"); // display value rounds, decision does not
    }

    @Test
    void detectsFullSector() {
        assertThat(new OccupancyRate(10, 10).isFull()).isTrue();
        assertThat(new OccupancyRate(9, 10).isFull()).isFalse();
    }

    @Test
    void shouldRejectInvalidPair() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OccupancyRate(1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new OccupancyRate(-1, 10));
    }
}
