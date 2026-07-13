package com.estapar.garage.garageconfiguration.infrastructure.inbound;

import com.estapar.garage.garageconfiguration.application.GarageReadiness;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports the garage configuration as UP only after the first successful sync. Included in the
 * {@code readiness} health group (see application.yml), so {@code /actuator/health/readiness}
 * stays DOWN — and the instance out of rotation — until the catalog is loaded (spec 07).
 * Liveness is unaffected: the process is alive while it waits for the simulator.
 */
@Component("garageConfiguration")
public class GarageReadinessHealthIndicator implements HealthIndicator {

    private final GarageReadiness readiness;

    public GarageReadinessHealthIndicator(GarageReadiness readiness) {
        this.readiness = readiness;
    }

    @Override
    public Health health() {
        return readiness.isReady()
                ? Health.up().withDetail("configuration", "synchronized").build()
                : Health.down().withDetail("configuration", "pending").build();
    }
}
