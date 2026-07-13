package com.estapar.garage.parking.domain;

import com.estapar.garage.parking.domain.exception.ParkingSpotOccupiedException;
import com.estapar.garage.shared.domain.Coordinates;
import java.util.Objects;

/** A physical spot. Occupation changes only through {@link #occupy}/{@link #release}. */
public class ParkingSpot {

    private final Long id;
    private final long externalId;
    private final String sectorCode;
    private final Coordinates coordinates;
    private SpotStatus status;
    private Long occupiedBySessionId;

    public ParkingSpot(
            Long id,
            long externalId,
            String sectorCode,
            Coordinates coordinates,
            SpotStatus status,
            Long occupiedBySessionId) {
        this.id = id;
        this.externalId = externalId;
        this.sectorCode = Objects.requireNonNull(sectorCode, "sectorCode");
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
        this.status = Objects.requireNonNull(status, "status");
        this.occupiedBySessionId = occupiedBySessionId;
    }

    public void occupy(long sessionId) {
        if (status == SpotStatus.OCCUPIED) {
            throw new ParkingSpotOccupiedException(externalId);
        }
        this.status = SpotStatus.OCCUPIED;
        this.occupiedBySessionId = sessionId;
    }

    public void release() {
        this.status = SpotStatus.AVAILABLE;
        this.occupiedBySessionId = null;
    }

    public boolean isAvailable() {
        return status == SpotStatus.AVAILABLE;
    }

    public Long id() {
        return id;
    }

    public long externalId() {
        return externalId;
    }

    public String sectorCode() {
        return sectorCode;
    }

    public Coordinates coordinates() {
        return coordinates;
    }

    public SpotStatus status() {
        return status;
    }

    public Long occupiedBySessionId() {
        return occupiedBySessionId;
    }
}
