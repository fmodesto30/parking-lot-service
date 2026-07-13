package com.estapar.garage.parking.domain.exception;

import com.estapar.garage.shared.domain.DomainException;

public class ParkingSpotOccupiedException extends DomainException {

    public ParkingSpotOccupiedException(long spotExternalId) {
        super("Parking spot " + spotExternalId + " is already occupied by another vehicle");
    }
}
