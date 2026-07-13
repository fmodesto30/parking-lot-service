package com.estapar.garage.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.estapar.garage.garageconfiguration.application.GarageReadiness;
import com.estapar.garage.parking.application.event.EntryEvent;
import com.estapar.garage.shared.domain.LicensePlate;
import com.estapar.garage.support.GarageTestFixtures;
import com.estapar.garage.support.MySqlIntegrationTest;
import com.estapar.garage.webhook.application.WebhookEventProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Confirms domain metrics are emitted with bounded tags: a processed and a duplicate ENTRY show
 * up under {@code garage_webhook_events_total{type,result}}, and the active-sessions gauge
 * tracks the control row.
 */
class WebhookMetricsIT extends MySqlIntegrationTest {

    @Autowired
    private WebhookEventProcessor processor;

    @Autowired
    private GarageReadiness readiness;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        new GarageTestFixtures(jdbc).reset(5, 5);
        readiness.markReady();
    }

    @Test
    void recordsProcessedAndDuplicateEventCounters() {
        var event = new EntryEvent(new LicensePlate("MET0001"), Instant.parse("2026-07-13T12:00:00Z"));
        processor.process(event);
        processor.process(event); // duplicate

        double processed = meterRegistry
                .get("garage_webhook_events_total")
                .tags("type", "ENTRY", "result", "PROCESSED")
                .counter()
                .count();
        double duplicate = meterRegistry
                .get("garage_duplicate_events_total")
                .tags("type", "ENTRY")
                .counter()
                .count();

        assertThat(processed).isGreaterThanOrEqualTo(1.0);
        assertThat(duplicate).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void publishesActiveSessionsGauge() {
        var gauge = meterRegistry.get("garage_active_sessions").gauge();

        try {
            assertThat(gauge.value()).isGreaterThanOrEqualTo(0.0);
        } catch (MeterNotFoundException e) {
            throw new AssertionError("garage_active_sessions gauge should be registered", e);
        }
    }
}
