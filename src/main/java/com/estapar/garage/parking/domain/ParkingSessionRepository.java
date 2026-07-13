package com.estapar.garage.parking.domain;

import com.estapar.garage.shared.domain.LicensePlate;
import java.util.Optional;

/** Port: parking session persistence. Lock methods acquire PESSIMISTIC_WRITE (ADR-003). */
public interface ParkingSessionRepository {

    ParkingSession save(ParkingSession session);

    Optional<ParkingSession> findActiveByPlate(LicensePlate plate);

    /** Locks the active session row for the plate for the current transaction. */
    Optional<ParkingSession> lockActiveByPlate(LicensePlate plate);

    long countActive();
}
