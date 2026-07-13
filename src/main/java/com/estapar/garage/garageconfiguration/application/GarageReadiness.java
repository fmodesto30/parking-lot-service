package com.estapar.garage.garageconfiguration.application;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory readiness signal: webhooks are rejected (503) and the readiness probe stays DOWN
 * until the garage configuration is available (spec 02 R6).
 */
public class GarageReadiness {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    public void markReady() {
        ready.set(true);
    }

    public boolean isReady() {
        return ready.get();
    }
}
