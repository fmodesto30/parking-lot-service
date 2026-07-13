package com.estapar.garage.webhook.infrastructure.inbound.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transport DTO mirroring the simulator's webhook payload (snake_case). Structural validation
 * ({@code license_plate}, {@code event_type} present) is declarative; the per-event-type field
 * rules are conditional and enforced in {@link WebhookEventMapper}.
 */
public record WebhookRequest(
        @JsonProperty("license_plate") @NotBlank String licensePlate,
        @JsonProperty("event_type") @NotNull EventType eventType,

        @JsonProperty("entry_time") @JsonDeserialize(using = LenientInstantDeserializer.class)
        Instant entryTime,

        @JsonProperty("exit_time") @JsonDeserialize(using = LenientInstantDeserializer.class)
        Instant exitTime,

        @JsonProperty("lat") BigDecimal lat,
        @JsonProperty("lng") BigDecimal lng) {}
