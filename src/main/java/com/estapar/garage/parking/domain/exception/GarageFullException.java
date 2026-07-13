package com.estapar.garage.parking.domain.exception;

import com.estapar.garage.shared.domain.DomainException;

public class GarageFullException extends DomainException {

    public GarageFullException(int totalCapacity) {
        super("Garage is full: all " + totalCapacity + " spots are taken; no capacity for a new vehicle");
    }
}
