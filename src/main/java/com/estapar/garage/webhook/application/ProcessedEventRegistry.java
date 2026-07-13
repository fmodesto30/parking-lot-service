package com.estapar.garage.webhook.application;

import java.time.Instant;

/**
 * Port: idempotency registry (ADR-004). {@link #register} must run inside the business
 * transaction so a rollback also forgets the fingerprint.
 */
public interface ProcessedEventRegistry {

    /**
     * Records the fingerprint. Returns {@code true} when this call registered it first;
     * {@code false} when the event was already processed (sequential duplicate) or lost a
     * concurrent race on the unique constraint.
     */
    boolean register(String fingerprint, String eventType, String licensePlate, Instant receivedAt, Long sessionId);

    boolean alreadyProcessed(String fingerprint);
}
