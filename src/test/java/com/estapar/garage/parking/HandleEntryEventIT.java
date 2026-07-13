package com.estapar.garage.parking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.estapar.garage.parking.application.EventOutcome;
import com.estapar.garage.parking.application.HandleEntryEventUseCase;
import com.estapar.garage.parking.application.event.EntryEvent;
import com.estapar.garage.parking.domain.exception.ActiveSessionAlreadyExistsException;
import com.estapar.garage.parking.domain.exception.GarageFullException;
import com.estapar.garage.shared.domain.LicensePlate;
import com.estapar.garage.support.GarageTestFixtures;
import com.estapar.garage.support.MySqlIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class HandleEntryEventIT extends MySqlIntegrationTest {

    @Autowired
    private HandleEntryEventUseCase useCase;

    @Autowired
    private JdbcTemplate jdbc;

    private GarageTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new GarageTestFixtures(jdbc);
        fixtures.reset(2, 3);
    }

    @Test
    void createsEnteredSessionAndIncrementsGlobalCount() {
        var outcome =
                useCase.execute(new EntryEvent(new LicensePlate("ZUL0001"), Instant.parse("2026-07-13T12:00:00Z")));

        assertThat(outcome).isEqualTo(EventOutcome.PROCESSED);
        assertThat(activeCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM parking_session WHERE license_plate='ZUL0001'", String.class))
                .isEqualTo("ENTERED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM processed_webhook_event WHERE event_type='ENTRY'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void duplicateEntryIsRecognizedAndNotReapplied() {
        var event = new EntryEvent(new LicensePlate("ZUL0001"), Instant.parse("2026-07-13T12:00:00Z"));

        assertThat(useCase.execute(event)).isEqualTo(EventOutcome.PROCESSED);
        assertThat(useCase.execute(event)).isEqualTo(EventOutcome.DUPLICATE);

        assertThat(activeCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parking_session", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void secondEntryForSamePlateWithDifferentTimeIsConflict() {
        useCase.execute(new EntryEvent(new LicensePlate("ZUL0001"), Instant.parse("2026-07-13T12:00:00Z")));

        assertThatThrownBy(() -> useCase.execute(
                        new EntryEvent(new LicensePlate("ZUL0001"), Instant.parse("2026-07-13T12:30:00Z"))))
                .isInstanceOf(ActiveSessionAlreadyExistsException.class);

        assertThat(activeCount()).isEqualTo(1);
    }

    @Test
    void rejectsEntryWhenGlobalCapacityIsExhausted() {
        fixtures.reset(1, 0);
        useCase.execute(new EntryEvent(new LicensePlate("AAA0001"), Instant.parse("2026-07-13T12:00:00Z")));

        assertThatThrownBy(() -> useCase.execute(
                        new EntryEvent(new LicensePlate("BBB0002"), Instant.parse("2026-07-13T12:00:01Z"))))
                .isInstanceOf(GarageFullException.class);

        assertThat(activeCount()).isEqualTo(1);
    }

    private int activeCount() {
        return jdbc.queryForObject("SELECT active_vehicle_count FROM garage_state WHERE id=1", Integer.class);
    }
}
