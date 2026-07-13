package com.estapar.garage.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LicensePlateTest {

    @Test
    void normalizesLowercaseAndSurroundingWhitespace() {
        assertThat(new LicensePlate("  zul0001 ").value()).isEqualTo("ZUL0001");
    }

    @Test
    void equalPlatesAfterNormalizationAreEqual() {
        assertThat(new LicensePlate("zul0001")).isEqualTo(new LicensePlate("ZUL0001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "AB CD", "PLATE!", "ABCDEFGHIJKLMNOPQ"})
    void shouldRejectBlankOrMalformedPlates(String raw) {
        assertThatIllegalArgumentException().isThrownBy(() -> new LicensePlate(raw));
    }

    @Test
    void masksMiddleForLogs() {
        assertThat(new LicensePlate("WDE56675").masked()).isEqualTo("WDE***75");
        assertThat(new LicensePlate("AB1").masked()).isEqualTo("****");
    }
}
