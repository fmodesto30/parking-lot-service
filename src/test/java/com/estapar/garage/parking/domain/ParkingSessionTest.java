package com.estapar.garage.parking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.estapar.garage.parking.domain.exception.InvalidExitTimeException;
import com.estapar.garage.parking.domain.exception.InvalidSessionTransitionException;
import com.estapar.garage.pricing.domain.TieredOccupancyPricingPolicy;
import com.estapar.garage.shared.domain.LicensePlate;
import com.estapar.garage.shared.domain.Money;
import java.time.Duration;
import java.time.Instant;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class ParkingSessionTest {

    private static final Instant ENTRY = Instant.parse("2026-07-13T12:00:00Z");
    private static final LicensePlate PLATE = new LicensePlate("ZUL0001");

    private ParkingSession enteredSession() {
        return ParkingSession.enter(PLATE, ENTRY);
    }

    private ParkingSession parkedSession() {
        var session = enteredSession();
        var price = new TieredOccupancyPricingPolicy().calculate(Money.of("40.50"), 2, 10);
        session.parkAt(7L, "A", price, ENTRY.plusSeconds(60));
        return session;
    }

    @Test
    void entryCreatesActiveSessionWithoutSpotOrPrice() {
        var session = enteredSession();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(session.status()).isEqualTo(SessionStatus.ENTERED);
            softly.assertThat(session.isActive()).isTrue();
            softly.assertThat(session.wasParked()).isFalse();
            softly.assertThat(session.appliedPrice()).isNull();
            softly.assertThat(session.sectorCode()).isNull();
        });
    }

    @Test
    void parkingFreezesSectorSpotAndPrice() {
        var session = parkedSession();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(session.status()).isEqualTo(SessionStatus.PARKED);
            softly.assertThat(session.spotId()).isEqualTo(7L);
            softly.assertThat(session.sectorCode()).isEqualTo("A");
            softly.assertThat(session.appliedPrice().effectiveHourlyPrice().amount())
                    .isEqualByComparingTo("36.45");
        });
    }

    @Test
    void shouldRejectParkingTwice() {
        var session = parkedSession();
        var price = new TieredOccupancyPricingPolicy().calculate(Money.of("40.50"), 3, 10);

        assertThatThrownBy(() -> session.parkAt(8L, "A", price, ENTRY.plusSeconds(120)))
                .isInstanceOf(InvalidSessionTransitionException.class);
    }

    @Test
    void exitFromParkedProducesBillableStay() {
        var session = parkedSession();

        session.exitAt(ENTRY.plus(Duration.ofMinutes(95)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(session.status()).isEqualTo(SessionStatus.EXITED);
            softly.assertThat(session.isActive()).isFalse();
            softly.assertThat(session.stayDuration()).isEqualTo(Duration.ofMinutes(95));
        });
    }

    @Test
    void exitStraightFromEnteredIsAllowedForVehicleThatNeverParked() {
        var session = enteredSession();

        session.exitAt(ENTRY.plusSeconds(10));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(session.status()).isEqualTo(SessionStatus.EXITED);
            softly.assertThat(session.wasParked()).isFalse();
        });
    }

    @Test
    void shouldRejectExitBeforeEntry() {
        var session = enteredSession();

        assertThatThrownBy(() -> session.exitAt(ENTRY.minusSeconds(1))).isInstanceOf(InvalidExitTimeException.class);
    }

    @Test
    void exitAtTheExactEntryInstantIsAllowed() {
        var session = enteredSession();

        session.exitAt(ENTRY);

        assertThat(session.stayDuration()).isZero();
    }

    @Test
    void shouldFreezeSessionAfterExit() {
        var session = parkedSession();
        session.exitAt(ENTRY.plusSeconds(3600));
        var price = new TieredOccupancyPricingPolicy().calculate(Money.of("40.50"), 1, 10);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThatThrownBy(() -> session.exitAt(ENTRY.plusSeconds(7200)))
                    .isInstanceOf(InvalidSessionTransitionException.class);
            softly.assertThatThrownBy(() -> session.parkAt(9L, "B", price, ENTRY.plusSeconds(7200)))
                    .isInstanceOf(InvalidSessionTransitionException.class);
        });
    }

    @Test
    void billingBeforeExitIsAProgrammingError() {
        assertThatThrownBy(() -> enteredSession().stayDuration()).isInstanceOf(InvalidSessionTransitionException.class);
    }
}
