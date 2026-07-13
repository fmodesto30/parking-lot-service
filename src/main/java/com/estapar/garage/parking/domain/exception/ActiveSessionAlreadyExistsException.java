package com.estapar.garage.parking.domain.exception;

import com.estapar.garage.shared.domain.DomainException;
import com.estapar.garage.shared.domain.LicensePlate;

public class ActiveSessionAlreadyExistsException extends DomainException {

    public ActiveSessionAlreadyExistsException(LicensePlate plate) {
        super("Plate " + plate.masked() + " already has an active parking session");
    }
}
