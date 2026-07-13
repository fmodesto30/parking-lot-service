package com.estapar.garage.webhook.infrastructure.inbound.web;

/** Closed set of webhook event types. An unknown value fails deserialization → 400. */
public enum EventType {
    ENTRY,
    PARKED,
    EXIT
}
