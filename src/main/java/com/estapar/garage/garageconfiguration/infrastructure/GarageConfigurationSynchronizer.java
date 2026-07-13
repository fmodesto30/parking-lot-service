package com.estapar.garage.garageconfiguration.infrastructure;

import com.estapar.garage.garageconfiguration.application.GarageCatalogFetchException;
import com.estapar.garage.garageconfiguration.application.SynchronizeGarageConfigurationUseCase;
import com.estapar.garage.shared.configuration.GarageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Triggers the initial configuration sync once the context is ready. Runs asynchronously so a
 * slow or absent simulator never blocks application startup — liveness stays UP, readiness
 * stays DOWN until the sync succeeds (spec 02 R6). Configurable off in tests.
 */
@Component
public class GarageConfigurationSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(GarageConfigurationSynchronizer.class);

    private final SynchronizeGarageConfigurationUseCase useCase;
    private final GarageProperties properties;

    public GarageConfigurationSynchronizer(SynchronizeGarageConfigurationUseCase useCase, GarageProperties properties) {
        this.useCase = useCase;
        this.properties = properties;
    }

    @Async
    @Order
    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeOnStartup() {
        if (!properties.simulator().sync().onStartup()) {
            log.info("garage_configuration_sync_skipped reason=disabled-by-configuration");
            return;
        }
        try {
            useCase.execute();
        } catch (GarageCatalogFetchException e) {
            // Do not crash-loop: fall back to any configuration already in the database.
            boolean recovered = useCase.recoverFromExistingConfiguration();
            if (!recovered) {
                log.error(
                        "garage_configuration_sync_failed recovered=false detail={} — service stays NOT READY",
                        e.getMessage());
            }
        }
    }
}
