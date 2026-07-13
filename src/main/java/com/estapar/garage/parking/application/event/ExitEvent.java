package com.estapar.garage.parking.application.event;

import com.estapar.garage.shared.domain.LicensePlate;
import java.time.Instant;
import java.util.Objects;

public record ExitEvent(LicensePlate plate, Instant exitTime) implements GarageEvent {

    public ExitEvent {
        Objects.requireNonNull(plate, "plate");
        Objects.requireNonNull(exitTime, "exitTime");
    }

    @Override
    public String type() {
        return "EXIT";
    }

    @Override
    public String fingerprintSeed() {
        return "EXIT|" + plate.value() + "|" + exitTime.toString();
    }
}
