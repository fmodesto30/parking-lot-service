package com.estapar.garage.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.estapar.garage.shared.domain.Money;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BillingCalculatorTest {

    private final BillingCalculator calculator = new BillingCalculator();

    @ParameterizedTest(name = "{0} → {1} billable hour(s)")
    @CsvSource({
        "PT29M59S,  0",
        "PT30M,     0",
        "PT30M1S,   1",
        "PT59M59S,  1",
        "PT1H,      1",
        "PT1H1S,    2",
        "PT1H59M59S,2",
        "PT2H,      2",
        "PT2H1S,    3",
        "PT0S,      0",
        "PT24H,     24",
    })
    void billsWholeStayRoundedUpAfterThirtyMinuteGrace(Duration stay, long expectedHours) {
        assertThat(calculator.billableHours(stay)).isEqualTo(expectedHours);
    }

    @Test
    void shouldNotChargeWhenVehicleStaysExactlyThirtyMinutes() {
        Money charge = calculator.charge(Duration.ofMinutes(30), Money.of("40.50"));

        assertThat(charge.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldChargeOneFullHourWhenStayExceedsGraceByOneSecond() {
        Money charge = calculator.charge(Duration.ofMinutes(30).plusSeconds(1), Money.of("40.50"));

        assertThat(charge.amount()).isEqualByComparingTo("40.50");
    }

    @Test
    void shouldChargeTwoHoursWhenVehicleStaysSixtyMinutesAndOneSecond() {
        Money charge = calculator.charge(Duration.ofMinutes(60).plusSeconds(1), Money.of("10.00"));

        assertThat(charge.amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldRoundSubSecondRemainderUpToNextHour() {
        assertThat(calculator.billableHours(Duration.ofHours(1).plusNanos(1))).isEqualTo(2);
    }

    @Test
    void shouldRejectNegativeStayAsProgrammingError() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.charge(Duration.ofSeconds(-1), Money.of("10.00")));
    }
}
