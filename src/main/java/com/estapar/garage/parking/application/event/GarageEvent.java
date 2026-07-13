package com.estapar.garage.parking.application.event;

import com.estapar.garage.shared.domain.LicensePlate;

/**
 * A parking lifecycle event, already parsed into domain value objects (the webhook adapter
 * translates the HTTP DTO into one of these). Sealed so the dispatcher's {@code switch} is
 * exhaustive without a default branch.
 *
 * <p>{@link #fingerprintSeed()} is the canonical string hashed for idempotency (ADR-004);
 * because the fields are already normalized VOs, the seed is deterministic across resends.
 */
public sealed interface GarageEvent permits EntryEvent, ParkedEvent, ExitEvent {

    LicensePlate plate();

    String type();

    String fingerprintSeed();
}
