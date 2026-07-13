package com.estapar.garage.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.estapar.garage.shared.domain.Money;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TieredOccupancyPricingPolicyTest {

    private final TieredOccupancyPricingPolicy policy = new TieredOccupancyPricingPolicy();

    @ParameterizedTest(name = "{0}/{1} occupied → multiplier {2}")
    @CsvSource({
        // capacity 10000 lets the table express exact fractional percentages
        "0,     10000, 0.90", // 0%
        "2499,  10000, 0.90", // 24.99%
        "2500,  10000, 1.00", // 25.00% — band edge is inclusive on the upper band
        "4999,  10000, 1.00", // 49.99%
        "5000,  10000, 1.10", // 50.00%
        "7499,  10000, 1.10", // 74.99%
        "7500,  10000, 1.25", // 75.00%
        "9999,  10000, 1.25", // 99.99%
        "2,     10,    0.90", // 20% at the real sector-A cardinality
        "5,     10,    1.10", // 50% must NOT round down into the 1.00 band
    })
    void appliesChallengeMultiplierTable(long occupied, long capacity, String expectedMultiplier) {
        AppliedPrice price = policy.calculate(Money.of("100.00"), occupied, capacity);

        assertThat(price.multiplier()).isEqualByComparingTo(expectedMultiplier);
    }

    @Test
    void freezesEffectivePriceAndOccupancySnapshotTogether() {
        AppliedPrice price = policy.calculate(Money.of("40.50"), 2, 10);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(price.basePrice().amount()).isEqualByComparingTo("40.50");
            softly.assertThat(price.multiplier()).isEqualByComparingTo("0.90");
            softly.assertThat(price.effectiveHourlyPrice().amount()).isEqualByComparingTo("36.45");
            softly.assertThat(price.occupancyRate().value()).isEqualByComparingTo("0.2000");
        });
    }

    @Test
    void roundsEffectivePriceHalfUpAtScaleTwo() {
        // 40.50 × 1.25 = 50.625 → 50.63
        AppliedPrice price = policy.calculate(Money.of("40.50"), 8, 10);

        assertThat(price.effectiveHourlyPrice().amount()).isEqualByComparingTo("50.63");
    }

    @Test
    void refusesToPriceAFullSector() {
        assertThatIllegalArgumentException().isThrownBy(() -> policy.calculate(Money.of("10.00"), 10, 10));
    }
}
