package com.estapar.garage.parking.domain.exception;

import com.estapar.garage.shared.domain.DomainException;
import com.estapar.garage.shared.domain.LicensePlate;

public class ActiveSessionNotFoundException extends DomainException {

    public ActiveSessionNotFoundException(LicensePlate plate) {
        super("No active parking session exists for plate " + plate.masked());
    }
}
