package com.estapar.garage.webhook.infrastructure.outbound.persistence;

import com.estapar.garage.shared.application.ProcessedEventRegistry;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Fingerprint registry (ADR-004). {@code saveAndFlush} forces the INSERT to hit the unique
 * index inside the caller's transaction: a concurrent duplicate blocks on the index entry and
 * surfaces as {@link DataIntegrityViolationException} after the winner commits — mapped here
 * to a plain {@code false} ("someone else already did it").
 */
@Component
public class ProcessedEventRegistryAdapter implements ProcessedEventRegistry {

    private final SpringDataProcessedWebhookEventRepository repository;
    private final Clock clock;

    public ProcessedEventRegistryAdapter(SpringDataProcessedWebhookEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public boolean register(
            String fingerprint, String eventType, String licensePlate, Instant receivedAt, Long sessionId) {
        if (repository.existsByFingerprint(fingerprint)) {
            return false;
        }
        try {
            repository.saveAndFlush(new ProcessedWebhookEventJpaEntity(
                    fingerprint, eventType, licensePlate, receivedAt, clock.instant(), sessionId));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Override
    public boolean alreadyProcessed(String fingerprint) {
        return repository.existsByFingerprint(fingerprint);
    }
}
