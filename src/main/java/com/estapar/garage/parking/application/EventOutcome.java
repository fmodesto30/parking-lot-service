package com.estapar.garage.parking.application;

/** Result of processing a webhook event: applied now, or recognized as an exact replay. */
public enum EventOutcome {
    PROCESSED,
    DUPLICATE
}
