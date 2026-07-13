package com.estapar.garage.parking.domain.exception;

import com.estapar.garage.shared.domain.Coordinates;
import com.estapar.garage.shared.domain.DomainException;

public class ParkingSpotNotFoundException extends DomainException {

    public ParkingSpotNotFoundException(Coordinates coordinates) {
        super("No parking spot exists at coordinates (" + coordinates.latitude() + ", " + coordinates.longitude()
                + ")");
    }
}
