package com.estapar.garage.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CoordinatesTest {

    @Test
    void normalizesDifferentScalesToSameCoordinate() {
        var sixDecimals = new Coordinates(new BigDecimal("-23.561684"), new BigDecimal("-46.655981"));
        var extraZeros = new Coordinates(new BigDecimal("-23.5616840000"), new BigDecimal("-46.65598100"));

        assertThat(sixDecimals).isEqualTo(extraZeros);
    }

    @Test
    void keepsSimulatorPrecision() {
        var coordinates = new Coordinates(new BigDecimal("-23.561684"), new BigDecimal("-46.655981"));

        assertThat(coordinates.latitude()).isEqualTo(new BigDecimal("-23.561684"));
        assertThat(coordinates.latitude().scale()).isEqualTo(6);
    }

    @Test
    void shouldRejectOutOfRangeCoordinates() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Coordinates(new BigDecimal("90.000001"), BigDecimal.ZERO));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Coordinates(BigDecimal.ZERO, new BigDecimal("-180.000001")));
    }
}
