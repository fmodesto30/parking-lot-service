package com.estapar.garage.garageconfiguration.application;

import com.estapar.garage.shared.domain.Coordinates;
import com.estapar.garage.shared.domain.Money;
import java.util.List;

/** Validated, domain-typed snapshot of the simulator's {@code GET /garage} response. */
public record GarageCatalog(List<CatalogSector> sectors, List<CatalogSpot> spots) {

    public record CatalogSector(String code, Money basePrice, int maxCapacity) {}

    public record CatalogSpot(long externalId, String sectorCode, Coordinates coordinates) {}

    public int totalCapacity() {
        return sectors.stream().mapToInt(CatalogSector::maxCapacity).sum();
    }
}
