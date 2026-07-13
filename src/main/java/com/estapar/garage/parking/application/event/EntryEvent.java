package com.estapar.garage.parking.application.event;

import com.estapar.garage.shared.domain.LicensePlate;
import java.time.Instant;
import java.util.Objects;

public record EntryEvent(LicensePlate plate, Instant entryTime) implements GarageEvent {

    public EntryEvent {
        Objects.requireNonNull(plate, "plate");
        Objects.requireNonNull(entryTime, "entryTime");
    }

    @Override
    public String type() {
        return "ENTRY";
    }

    @Override
    public String fingerprintSeed() {
        return "ENTRY|" + plate.value() + "|" + entryTime.toString();
    }
}
