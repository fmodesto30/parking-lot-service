package com.estapar.garage.parking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.estapar.garage.parking.application.EventOutcome;
import com.estapar.garage.parking.application.HandleEntryEventUseCase;
import com.estapar.garage.parking.application.HandleParkedEventUseCase;
import com.estapar.garage.parking.application.event.EntryEvent;
import com.estapar.garage.parking.application.event.ParkedEvent;
import com.estapar.garage.parking.domain.exception.SectorFullException;
import com.estapar.garage.shared.domain.Coordinates;
import com.estapar.garage.shared.domain.LicensePlate;
import com.estapar.garage.support.GarageTestFixtures;
import com.estapar.garage.support.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class HandleParkedEventIT extends MySqlIntegrationTest {

    @Autowired
    private HandleEntryEventUseCase entryUseCase;

    @Autowired
    private HandleParkedEventUseCase parkedUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    private GarageTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new GarageTestFixtures(jdbc);
        fixtures.reset(10, 20);
    }

    private void enter(String plate) {
        entryUseCase.execute(new EntryEvent(new LicensePlate(plate), Instant.parse("2026-07-13T12:00:00Z")));
    }

    private Coordinates spotA(int index) {
        double[] c = fixtures.spotCoordinates("A", index);
        return new Coordinates(BigDecimal.valueOf(c[0]), BigDecimal.valueOf(c[1]));
    }

    @Test
    void parksVehicleOccupiesSpotAndFreezesPriceAtEntryBand() {
        enter("ZUL0001");

        var outcome = parkedUseCase.execute(new ParkedEvent(new LicensePlate("ZUL0001"), spotA(1)));

        assertThat(outcome).isEqualTo(EventOutcome.PROCESSED);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM parking_session WHERE license_plate='ZUL0001'", String.class))
                .isEqualTo("PARKED");
        assertThat(jdbc.queryForObject("SELECT status FROM parking_spot WHERE external_id=1", String.class))
                .isEqualTo("OCCUPIED");
        // Empty sector before this car → 0% occupancy → 0.90 multiplier → 40.50 * 0.90 = 36.45
        assertThat(jdbc.queryForObject(
                        "SELECT effective_hourly_price FROM parking_session WHERE license_plate='ZUL0001'",
                        BigDecimal.class))
                .isEqualByComparingTo("36.45");
        assertThat(jdbc.queryForObject(
                        "SELECT price_multiplier_snapshot FROM parking_session WHERE license_plate='ZUL0001'",
                        BigDecimal.class))
                .isEqualByComparingTo("0.90");
    }

    @Test
    void pricesJumpAcrossOccupancyBandsAsSectorFills() {
        // Sector A capacity 10. Park cars on spots 1..4; the 4th sees 3/10 = 30% → 1.00 band.
        for (int i = 1; i <= 4; i++) {
            enter("CAR000" + i);
            parkedUseCase.execute(new ParkedEvent(new LicensePlate("CAR000" + i), spotA(i)));
        }

        var fourth = jdbc.queryForObject(
                "SELECT price_multiplier_snapshot FROM parking_session WHERE license_plate='CAR0004'",
                BigDecimal.class);
        assertThat(fourth).isEqualByComparingTo("1.00");
    }

    @Test
    void rejectsParkedIntoAFullSectorWithSpareSpot() {
        // capacity 1 but two physical spots → the 2nd park hits the 100% sector gate, not a spot clash.
        fixtures.reset(1, 5);
        jdbc.update("INSERT INTO parking_spot(external_id, sector_code, latitude, longitude, status, version,"
                + " created_at, updated_at) VALUES (2, 'A', -23.500000, -46.500000, 'AVAILABLE', 0, NOW(6), NOW(6))");
        enter("FULL001");
        parkedUseCase.execute(new ParkedEvent(new LicensePlate("FULL001"), spotA(1)));
        enter("FULL002");

        assertThatThrownBy(() -> parkedUseCase.execute(new ParkedEvent(
                        new LicensePlate("FULL002"),
                        new Coordinates(new BigDecimal("-23.500000"), new BigDecimal("-46.500000")))))
                .isInstanceOf(SectorFullException.class);
    }
}
