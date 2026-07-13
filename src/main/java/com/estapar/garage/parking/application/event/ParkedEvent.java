package com.estapar.garage.parking.application.event;

import com.estapar.garage.shared.domain.Coordinates;
import com.estapar.garage.shared.domain.LicensePlate;
import java.util.Objects;

public record ParkedEvent(LicensePlate plate, Coordinates coordinates) implements GarageEvent {

    public ParkedEvent {
        Objects.requireNonNull(plate, "plate");
        Objects.requireNonNull(coordinates, "coordinates");
    }

    @Override
    public String type() {
        return "PARKED";
    }

    @Override
    public String fingerprintSeed() {
        return "PARKED|" + plate.value() + "|" + coordinates.latitude() + "|" + coordinates.longitude();
    }
}
