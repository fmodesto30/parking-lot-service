package com.estapar.garage.shared.observability;

import com.estapar.garage.parking.domain.GarageStateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code garage_active_sessions} as a gauge sourced from the single control row. Read
 * lazily on each scrape; the gauge holds no plate/session detail (bounded cardinality).
 */
@Component
public class GarageStateMetricsBinder implements MeterBinder {

    private final GarageStateRepository garageStateRepository;

    public GarageStateMetricsBinder(GarageStateRepository garageStateRepository) {
        this.garageStateRepository = garageStateRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge("garage_active_sessions", this, binder -> binder.currentActiveCount());
    }

    private double currentActiveCount() {
        try {
            return garageStateRepository.get().activeVehicleCount();
        } catch (RuntimeException e) {
            return Double.NaN; // no control row yet (pre-migration) → report unknown, don't fail scrape
        }
    }
}
