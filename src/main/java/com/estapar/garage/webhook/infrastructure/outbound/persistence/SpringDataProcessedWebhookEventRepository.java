package com.estapar.garage.webhook.infrastructure.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEventJpaEntity, Long> {

    boolean existsByFingerprint(String fingerprint);
}
