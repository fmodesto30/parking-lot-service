package com.estapar.garage.garageconfiguration.domain.exception;

import com.estapar.garage.shared.domain.DomainException;

public class SectorNotFoundException extends DomainException {

    public SectorNotFoundException(String sectorCode) {
        super("Sector does not exist: " + sectorCode);
    }
}
