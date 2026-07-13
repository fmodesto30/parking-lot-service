package com.estapar.garage.webhook.infrastructure.inbound.web;

import com.estapar.garage.parking.application.event.EntryEvent;
import com.estapar.garage.parking.application.event.ExitEvent;
import com.estapar.garage.parking.application.event.GarageEvent;
import com.estapar.garage.parking.application.event.ParkedEvent;
import com.estapar.garage.shared.domain.Coordinates;
import com.estapar.garage.shared.domain.LicensePlate;
import com.estapar.garage.webhook.application.InvalidWebhookEventException;
import org.springframework.stereotype.Component;

/**
 * Translates the transport DTO into a domain event, enforcing the conditional per-type field
 * rules (spec 03): required fields must be present and incompatible fields must be absent, so
 * an ambiguous payload (e.g. ENTRY carrying {@code exit_time}) is rejected with a 400 rather
 * than silently accepted.
 */
@Component
public class WebhookEventMapper {

    public GarageEvent toDomainEvent(WebhookRequest request) {
        if (request.eventType() == null) {
            throw new InvalidWebhookEventException("event_type is required");
        }
        LicensePlate plate = parsePlate(request.licensePlate());
        return switch (request.eventType()) {
            case ENTRY -> toEntry(request, plate);
            case PARKED -> toParked(request, plate);
            case EXIT -> toExit(request, plate);
        };
    }

    private GarageEvent toEntry(WebhookRequest request, LicensePlate plate) {
        require(request.entryTime() != null, "entry_time is required for ENTRY");
        reject(
                request.exitTime() != null || request.lat() != null || request.lng() != null,
                "ENTRY must not carry exit_time, lat or lng");
        return new EntryEvent(plate, request.entryTime());
    }

    private GarageEvent toParked(WebhookRequest request, LicensePlate plate) {
        require(request.lat() != null && request.lng() != null, "lat and lng are required for PARKED");
        reject(
                request.entryTime() != null || request.exitTime() != null,
                "PARKED must not carry entry_time or exit_time");
        return new ParkedEvent(plate, parseCoordinates(request.lat(), request.lng()));
    }

    private GarageEvent toExit(WebhookRequest request, LicensePlate plate) {
        require(request.exitTime() != null, "exit_time is required for EXIT");
        reject(request.lat() != null || request.lng() != null, "EXIT must not carry lat or lng");
        return new ExitEvent(plate, request.exitTime());
    }

    private LicensePlate parsePlate(String rawPlate) {
        if (rawPlate == null || rawPlate.isBlank()) {
            throw new InvalidWebhookEventException("license_plate is required");
        }
        try {
            return new LicensePlate(rawPlate);
        } catch (IllegalArgumentException e) {
            throw new InvalidWebhookEventException(e.getMessage());
        }
    }

    private Coordinates parseCoordinates(java.math.BigDecimal lat, java.math.BigDecimal lng) {
        try {
            return new Coordinates(lat, lng);
        } catch (IllegalArgumentException e) {
            throw new InvalidWebhookEventException(e.getMessage());
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidWebhookEventException(message);
        }
    }

    private void reject(boolean condition, String message) {
        if (condition) {
            throw new InvalidWebhookEventException(message);
        }
    }
}
