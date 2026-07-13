package com.estapar.garage.parking.infrastructure.outbound.persistence;

import com.estapar.garage.parking.domain.ParkingSession;
import com.estapar.garage.parking.domain.ParkingSessionRepository;
import com.estapar.garage.pricing.domain.AppliedPrice;
import com.estapar.garage.shared.domain.LicensePlate;
import com.estapar.garage.shared.domain.Money;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ParkingSessionRepositoryAdapter implements ParkingSessionRepository {

    private final SpringDataParkingSessionRepository repository;

    public ParkingSessionRepositoryAdapter(SpringDataParkingSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public ParkingSession save(ParkingSession session) {
        ParkingSessionJpaEntity entity;
        if (session.id() == null) {
            entity = new ParkingSessionJpaEntity(
                    session.licensePlate().value(),
                    session.isActive() ? session.licensePlate().value() : null,
                    session.status(),
                    session.entryTime());
        } else {
            entity = repository
                    .findById(session.id())
                    .orElseThrow(
                            () -> new IllegalStateException("Session disappeared mid-transaction: " + session.id()));
        }
        copyMutableState(session, entity);
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<ParkingSession> findActiveByPlate(LicensePlate plate) {
        return repository.findByActivePlate(plate.value()).map(this::toDomain);
    }

    @Override
    public Optional<ParkingSession> lockActiveByPlate(LicensePlate plate) {
        return repository.lockByActivePlate(plate.value()).map(this::toDomain);
    }

    @Override
    public long countActive() {
        return repository.countByActivePlateIsNotNull();
    }

    private void copyMutableState(ParkingSession session, ParkingSessionJpaEntity entity) {
        entity.setStatus(session.status());
        entity.setActivePlate(session.isActive() ? session.licensePlate().value() : null);
        entity.setParkedAt(session.parkedAt());
        entity.setExitTime(session.exitTime());
        entity.setSpotId(session.spotId());
        entity.setSectorCode(session.sectorCode());
        AppliedPrice price = session.appliedPrice();
        if (price != null) {
            entity.setBasePriceSnapshot(price.basePrice().amount());
            entity.setOccupancyRateSnapshot(price.occupancyRate());
            entity.setPriceMultiplierSnapshot(price.multiplier());
            entity.setEffectiveHourlyPrice(price.effectiveHourlyPrice().amount());
        }
    }

    private ParkingSession toDomain(ParkingSessionJpaEntity entity) {
        AppliedPrice price = entity.getEffectiveHourlyPrice() == null
                ? null
                : new AppliedPrice(
                        Money.of(entity.getBasePriceSnapshot()),
                        entity.getOccupancyRateSnapshot(),
                        entity.getPriceMultiplierSnapshot(),
                        Money.of(entity.getEffectiveHourlyPrice()));
        return ParkingSession.restore(
                entity.getId(),
                new LicensePlate(entity.getLicensePlate()),
                entity.getStatus(),
                entity.getEntryTime(),
                entity.getParkedAt(),
                entity.getExitTime(),
                entity.getSpotId(),
                entity.getSectorCode(),
                price);
    }
}
