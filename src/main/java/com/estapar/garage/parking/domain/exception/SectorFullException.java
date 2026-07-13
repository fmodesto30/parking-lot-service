package com.estapar.garage.parking.domain.exception;

import com.estapar.garage.shared.domain.DomainException;

public class SectorFullException extends DomainException {

    public SectorFullException(String sectorCode) {
        super("Sector " + sectorCode + " is at 100% occupancy and closed until a vehicle leaves");
    }
}
