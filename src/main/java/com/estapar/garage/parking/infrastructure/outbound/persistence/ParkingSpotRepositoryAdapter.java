package com.estapar.garage.parking.infrastructure.outbound.persistence;

import com.estapar.garage.parking.domain.ParkingSpot;
import com.estapar.garage.parking.domain.ParkingSpotRepository;
import com.estapar.garage.parking.domain.SpotStatus;
import com.estapar.garage.shared.domain.Coordinates;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ParkingSpotRepositoryAdapter implements ParkingSpotRepository {

    private final SpringDataParkingSpotRepository repository;

    public ParkingSpotRepositoryAdapter(SpringDataParkingSpotRepository repository) {
        this.repository = repository;
    }

    @Override
    public ParkingSpot save(ParkingSpot spot) {
        ParkingSpotJpaEntity entity;
        if (spot.id() == null) {
            entity = new ParkingSpotJpaEntity(
                    spot.externalId(),
                    spot.sectorCode(),
                    spot.coordinates().latitude(),
                    spot.coordinates().longitude(),
                    spot.status(),
                    spot.occupiedBySessionId());
        } else {
            entity = repository
                    .findById(spot.id())
                    .orElseThrow(() -> new IllegalStateException("Spot disappeared mid-transaction: " + spot.id()));
            entity.setSectorCode(spot.sectorCode());
            entity.setLatitude(spot.coordinates().latitude());
            entity.setLongitude(spot.coordinates().longitude());
            entity.setStatus(spot.status());
            entity.setOccupiedBySessionId(spot.occupiedBySessionId());
        }
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<ParkingSpot> findByExternalId(long externalId) {
        return repository.findByExternalId(externalId).map(this::toDomain);
    }

    @Override
    public Optional<ParkingSpot> lockByCoordinates(Coordinates coordinates) {
        return repository
                .lockByCoordinates(coordinates.latitude(), coordinates.longitude())
                .map(this::toDomain);
    }

    @Override
    public Optional<ParkingSpot> lockById(long id) {
        return repository.lockById(id).map(this::toDomain);
    }

    @Override
    public long countOccupiedBySector(String sectorCode) {
        return repository.countBySectorCodeAndStatus(sectorCode, SpotStatus.OCCUPIED);
    }

    private ParkingSpot toDomain(ParkingSpotJpaEntity entity) {
        return new ParkingSpot(
                entity.getId(),
                entity.getExternalId(),
                entity.getSectorCode(),
                new Coordinates(entity.getLatitude(), entity.getLongitude()),
                entity.getStatus(),
                entity.getOccupiedBySessionId());
    }
}
