package com.estapar.garage.parking;

import static org.assertj.core.api.Assertions.assertThat;

import com.estapar.garage.parking.application.EventOutcome;
import com.estapar.garage.parking.application.HandleEntryEventUseCase;
import com.estapar.garage.parking.application.HandleExitEventUseCase;
import com.estapar.garage.parking.application.HandleParkedEventUseCase;
import com.estapar.garage.parking.application.event.EntryEvent;
import com.estapar.garage.parking.application.event.ExitEvent;
import com.estapar.garage.parking.application.event.ParkedEvent;
import com.estapar.garage.shared.domain.Coordinates;
import com.estapar.garage.shared.domain.LicensePlate;
import com.estapar.garage.support.GarageTestFixtures;
import com.estapar.garage.support.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class HandleExitEventIT extends MySqlIntegrationTest {

    private static final Instant ENTRY = Instant.parse("2026-07-13T12:00:00Z");

    @Autowired
    private HandleEntryEventUseCase entryUseCase;

    @Autowired
    private HandleParkedEventUseCase parkedUseCase;

    @Autowired
    private HandleExitEventUseCase exitUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    private GarageTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new GarageTestFixtures(jdbc);
        fixtures.reset(10, 20);
    }

    private Coordinates spotA(int index) {
        double[] c = fixtures.spotCoordinates("A", index);
        return new Coordinates(BigDecimal.valueOf(c[0]), BigDecimal.valueOf(c[1]));
    }

    private void enterAndPark(String plate, int spotIndex) {
        entryUseCase.execute(new EntryEvent(new LicensePlate(plate), ENTRY));
        parkedUseCase.execute(new ParkedEvent(new LicensePlate(plate), spotA(spotIndex)));
    }

    @Test
    void completesChargedLifecycleReleasesSpotAndDecrementsCount() {
        enterAndPark("ZUL0001", 1);

        // First parked → 0% band → 36.45/h. 95 min → 2h → 72.90.
        var outcome =
                exitUseCase.execute(new ExitEvent(new LicensePlate("ZUL0001"), ENTRY.plus(Duration.ofMinutes(95))));

        assertThat(outcome).isEqualTo(EventOutcome.PROCESSED);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM parking_session WHERE license_plate='ZUL0001'", String.class))
                .isEqualTo("EXITED");
        assertThat(jdbc.queryForObject("SELECT status FROM parking_spot WHERE external_id=1", String.class))
                .isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT active_vehicle_count FROM garage_state WHERE id=1", Integer.class))
                .isEqualTo(0);
        assertThat(jdbc.queryForObject(
                        "SELECT amount FROM parking_charge WHERE session_id="
                                + "(SELECT id FROM parking_session WHERE license_plate='ZUL0001')",
                        BigDecimal.class))
                .isEqualByComparingTo("72.90");
    }

    @Test
    void freeStayUnderThirtyMinutesChargesZeroAndReleasesSpot() {
        enterAndPark("FREE001", 1);

        exitUseCase.execute(new ExitEvent(new LicensePlate("FREE001"), ENTRY.plus(Duration.ofMinutes(30))));

        assertThat(jdbc.queryForObject(
                        "SELECT amount FROM parking_charge WHERE session_id="
                                + "(SELECT id FROM parking_session WHERE license_plate='FREE001')",
                        BigDecimal.class))
                .isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT status FROM parking_spot WHERE external_id=1", String.class))
                .isEqualTo("AVAILABLE");
    }

    @Test
    void vehicleThatNeverParkedExitsWithoutCharge() {
        entryUseCase.execute(new EntryEvent(new LicensePlate("PASS001"), ENTRY));

        exitUseCase.execute(new ExitEvent(new LicensePlate("PASS001"), ENTRY.plus(Duration.ofMinutes(5))));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parking_charge", Long.class))
                .isEqualTo(0L);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM parking_session WHERE license_plate='PASS001'", String.class))
                .isEqualTo("EXITED");
        assertThat(jdbc.queryForObject("SELECT active_vehicle_count FROM garage_state WHERE id=1", Integer.class))
                .isEqualTo(0);
    }

    @Test
    void duplicateExitDoesNotChargeTwice() {
        enterAndPark("DUP0001", 1);
        var exit = new ExitEvent(new LicensePlate("DUP0001"), ENTRY.plus(Duration.ofMinutes(90)));

        assertThat(exitUseCase.execute(exit)).isEqualTo(EventOutcome.PROCESSED);
        assertThat(exitUseCase.execute(exit)).isEqualTo(EventOutcome.DUPLICATE);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parking_charge", Long.class))
                .isEqualTo(1L);
    }
}
