package com.estapar.garage.garageconfiguration.infrastructure.inbound;

import com.estapar.garage.garageconfiguration.application.GarageReadiness;
import org.springframework.boot.actuate.availability.ReadinessStateHealthIndicator;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.stereotype.Component;

/**
 * Ties Kubernetes-style readiness to configuration availability: the app is only READY once
 * {@link GarageReadiness} reports the garage catalog is loaded (spec 07). Liveness is
 * untouched — the process is alive even while it waits for the simulator.
 */
@Component
public class GarageReadinessHealthIndicator extends ReadinessStateHealthIndicator {

    private final GarageReadiness readiness;

    public GarageReadinessHealthIndicator(ApplicationAvailability availability, GarageReadiness readiness) {
        super(availability);
        this.readiness = readiness;
    }

    @Override
    protected AvailabilityState getState(ApplicationAvailability applicationAvailability) {
        return readiness.isReady() ? ReadinessState.ACCEPTING_TRAFFIC : ReadinessState.REFUSING_TRAFFIC;
    }
}
