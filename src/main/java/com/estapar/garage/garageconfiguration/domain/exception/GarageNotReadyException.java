package com.estapar.garage.garageconfiguration.domain.exception;

import com.estapar.garage.shared.domain.DomainException;

public class GarageNotReadyException extends DomainException {

    public GarageNotReadyException() {
        super("Garage configuration has not been synchronized yet; try again shortly");
    }
}
