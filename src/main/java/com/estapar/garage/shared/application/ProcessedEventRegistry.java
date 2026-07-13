package com.estapar.garage.shared.application;

import java.time.Instant;

/**
 * Port: idempotency registry (ADR-004). A shared concern — produced by the webhook module,
 * consumed by the parking use cases — so it lives in {@code shared} to keep both modules
 * pointing inward. {@link #register} must run inside the business transaction so a rollback
 * also forgets the fingerprint.
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
