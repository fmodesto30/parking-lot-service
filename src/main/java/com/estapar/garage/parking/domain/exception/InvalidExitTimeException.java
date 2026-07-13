package com.estapar.garage.parking.domain.exception;

import com.estapar.garage.shared.domain.DomainException;
import java.time.Instant;

public class InvalidExitTimeException extends DomainException {

    public InvalidExitTimeException(Instant exitTime, Instant entryTime) {
        super("Exit time " + exitTime + " is before entry time " + entryTime);
    }
}
