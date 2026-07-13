package com.estapar.garage.webhook.infrastructure.inbound.web;

import com.estapar.garage.parking.application.EventOutcome;

/** Webhook acknowledgement. Both PROCESSED and DUPLICATE answer HTTP 200 (spec 03). */
public record WebhookResponse(String result, String eventType) {

    public static WebhookResponse of(EventOutcome outcome, String eventType) {
        return new WebhookResponse(outcome.name(), eventType);
    }
}
