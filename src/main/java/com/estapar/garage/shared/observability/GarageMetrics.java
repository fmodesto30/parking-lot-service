package com.estapar.garage.shared.observability;

import com.estapar.garage.parking.application.EventOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Domain metrics with bounded tag cardinality (spec 07): tags are only the event type, a coarse
 * result/reason or a resource name — never plate, session id or coordinates. Counters are
 * created lazily by the registry, so tag combinations appear as they occur.
 */
@Component
public class GarageMetrics {

    private final MeterRegistry registry;

    public GarageMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startProcessing() {
        return Timer.start(registry);
    }

    public void recordProcessing(Timer.Sample sample, String eventType) {
        sample.stop(registry.timer("garage_event_processing_duration", "type", eventType));
    }

    public void eventProcessed(String eventType, EventOutcome outcome) {
        registry.counter("garage_webhook_events_total", "type", eventType, "result", outcome.name())
                .increment();
        if (outcome == EventOutcome.DUPLICATE) {
            registry.counter("garage_duplicate_events_total", "type", eventType).increment();
        }
    }

    public void eventRejected(String eventType, String reason) {
        registry.counter("garage_webhook_events_total", "type", eventType, "result", "REJECTED")
                .increment();
        registry.counter("garage_webhook_rejections_total", "type", eventType, "reason", reason)
                .increment();
    }

    public void concurrencyConflict(String resource) {
        registry.counter("garage_concurrency_conflicts_total", "resource", resource)
                .increment();
    }

    public void configurationSyncFailure() {
        registry.counter("garage_configuration_sync_failures_total").increment();
    }
}
