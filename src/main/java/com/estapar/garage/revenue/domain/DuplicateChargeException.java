package com.estapar.garage.revenue.domain;

import com.estapar.garage.shared.domain.DomainException;

public class DuplicateChargeException extends DomainException {

    public DuplicateChargeException(long sessionId) {
        super("Session " + sessionId + " already has a charge; charges are immutable and unique");
    }
}
