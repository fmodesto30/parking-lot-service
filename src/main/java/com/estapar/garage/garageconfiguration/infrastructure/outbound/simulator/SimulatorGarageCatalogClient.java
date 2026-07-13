package com.estapar.garage.garageconfiguration.infrastructure.outbound.simulator;

import com.estapar.garage.garageconfiguration.application.GarageCatalog;
import com.estapar.garage.garageconfiguration.application.GarageCatalog.CatalogSector;
import com.estapar.garage.garageconfiguration.application.GarageCatalog.CatalogSpot;
import com.estapar.garage.garageconfiguration.application.GarageCatalogClient;
import com.estapar.garage.garageconfiguration.application.GarageCatalogFetchException;
import com.estapar.garage.shared.domain.Coordinates;
import com.estapar.garage.shared.domain.Money;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adapter: simulator integration over blocking {@link RestClient} with explicit timeouts
 * (configured in {@link SimulatorClientConfiguration}). Validates the payload structurally
 * before it crosses into the application layer.
 */
@Component
public class SimulatorGarageCatalogClient implements GarageCatalogClient {

    private final RestClient restClient;

    public SimulatorGarageCatalogClient(RestClient simulatorRestClient) {
        this.restClient = simulatorRestClient;
    }

    @Override
    public GarageCatalog fetch() {
        GarageResponse response;
        try {
            response = restClient.get().uri("/garage").retrieve().body(GarageResponse.class);
        } catch (RestClientException e) {
            throw new GarageCatalogFetchException("Simulator GET /garage failed: " + e.getMessage(), e);
        }
        return validate(response);
    }

    private GarageCatalog validate(GarageResponse response) {
        if (response == null || response.garage() == null || response.garage().isEmpty()) {
            throw new GarageCatalogFetchException("Garage payload has no sectors");
        }
        if (response.spots() == null || response.spots().isEmpty()) {
            throw new GarageCatalogFetchException("Garage payload has no spots");
        }

        Set<String> sectorCodes = new HashSet<>();
        List<CatalogSector> sectors = response.garage().stream()
                .map(payload -> toSector(payload, sectorCodes))
                .toList();

        Set<Long> spotIds = new HashSet<>();
        Set<Coordinates> spotCoordinates = new HashSet<>();
        List<CatalogSpot> spots = response.spots().stream()
                .map(payload -> toSpot(payload, sectorCodes, spotIds, spotCoordinates))
                .toList();

        return new GarageCatalog(sectors, spots);
    }

    private CatalogSector toSector(GarageResponse.SectorPayload payload, Set<String> seenCodes) {
        if (payload.sector() == null || payload.sector().isBlank()) {
            throw new GarageCatalogFetchException("Sector with blank code in garage payload");
        }
        if (payload.basePrice() == null || payload.basePrice().signum() < 0) {
            throw new GarageCatalogFetchException("Sector " + payload.sector() + " has invalid base price");
        }
        if (payload.maxCapacity() == null || payload.maxCapacity() <= 0) {
            throw new GarageCatalogFetchException("Sector " + payload.sector() + " has invalid max capacity");
        }
        if (!seenCodes.add(payload.sector())) {
            throw new GarageCatalogFetchException("Duplicated sector code: " + payload.sector());
        }
        return new CatalogSector(payload.sector(), Money.of(payload.basePrice()), payload.maxCapacity());
    }

    private CatalogSpot toSpot(
            GarageResponse.SpotPayload payload, Set<String> sectorCodes, Set<Long> seenIds, Set<Coordinates> seen) {
        if (payload.id() == null) {
            throw new GarageCatalogFetchException("Spot without id in garage payload");
        }
        if (payload.sector() == null || !sectorCodes.contains(payload.sector())) {
            throw new GarageCatalogFetchException(
                    "Spot " + payload.id() + " references unknown sector " + payload.sector());
        }
        if (payload.lat() == null || payload.lng() == null) {
            throw new GarageCatalogFetchException("Spot " + payload.id() + " is missing coordinates");
        }
        if (!seenIds.add(payload.id())) {
            throw new GarageCatalogFetchException("Duplicated spot id: " + payload.id());
        }
        Coordinates coordinates = new Coordinates(payload.lat(), payload.lng());
        if (!seen.add(coordinates)) {
            throw new GarageCatalogFetchException("Duplicated spot coordinates: " + coordinates);
        }
        return new CatalogSpot(payload.id(), payload.sector(), coordinates);
    }
}
