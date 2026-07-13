package com.estapar.garage.parking.domain;

import com.estapar.garage.shared.domain.Coordinates;
import java.util.Optional;

/** Port: physical spot persistence. Lock methods acquire PESSIMISTIC_WRITE (ADR-003). */
public interface ParkingSpotRepository {

    ParkingSpot save(ParkingSpot spot);

    /** Locks the spot at the exact coordinates for the current transaction. */
    Optional<ParkingSpot> lockByCoordinates(Coordinates coordinates);

    /** Locks the spot by internal id for the current transaction. */
    Optional<ParkingSpot> lockById(long id);

    long countOccupiedBySector(String sectorCode);
}
