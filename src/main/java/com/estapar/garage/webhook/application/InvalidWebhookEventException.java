package com.estapar.garage.webhook.application;

import com.estapar.garage.shared.domain.DomainException;

public class InvalidWebhookEventException extends DomainException {

    public InvalidWebhookEventException(String reason) {
        super("Invalid webhook event: " + reason);
    }
}
