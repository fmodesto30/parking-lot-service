package com.estapar.garage.parking.domain.exception;

import com.estapar.garage.shared.domain.DomainException;

public class InvalidSessionTransitionException extends DomainException {

    public InvalidSessionTransitionException(String currentStatus, String attempted) {
        super("Session in status " + currentStatus + " does not allow transition " + attempted);
    }
}
