package com.estapar.garage.webhook.infrastructure.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_webhook_event")
public class ProcessedWebhookEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "event_type", nullable = false, length = 16)
    private String eventType;

    @Column(name = "license_plate", nullable = false, length = 16)
    private String licensePlate;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "session_id")
    private Long sessionId;

    protected ProcessedWebhookEventJpaEntity() {}

    public ProcessedWebhookEventJpaEntity(
            String fingerprint,
            String eventType,
            String licensePlate,
            Instant receivedAt,
            Instant processedAt,
            Long sessionId) {
        this.fingerprint = fingerprint;
        this.eventType = eventType;
        this.licensePlate = licensePlate;
        this.receivedAt = receivedAt;
        this.processedAt = processedAt;
        this.sessionId = sessionId;
    }

    public Long getId() {
        return id;
    }

    public String getFingerprint() {
        return fingerprint;
    }
}
