package com.estapar.garage.parking.infrastructure.outbound.persistence;

import com.estapar.garage.parking.domain.GarageState;
import com.estapar.garage.parking.domain.GarageStateRepository;
import org.springframework.stereotype.Component;

@Component
public class GarageStateRepositoryAdapter implements GarageStateRepository {

    private final SpringDataGarageStateRepository repository;

    public GarageStateRepositoryAdapter(SpringDataGarageStateRepository repository) {
        this.repository = repository;
    }

    @Override
    public GarageState lock() {
        return repository.lockSingleton().map(this::toDomain).orElseThrow(this::missingControlRow);
    }

    @Override
    public GarageState get() {
        return repository.findById(1L).map(this::toDomain).orElseThrow(this::missingControlRow);
    }

    @Override
    public void save(GarageState state) {
        var entity = repository.findById(state.id()).orElseThrow(this::missingControlRow);
        entity.setTotalCapacity(state.totalCapacity());
        entity.setActiveVehicleCount(state.activeVehicleCount());
        entity.setLastSynchronizedAt(state.lastSynchronizedAt());
        repository.save(entity);
    }

    private GarageState toDomain(GarageStateJpaEntity entity) {
        return new GarageState(
                entity.getId(),
                entity.getTotalCapacity(),
                entity.getActiveVehicleCount(),
                entity.getLastSynchronizedAt());
    }

    private IllegalStateException missingControlRow() {
        return new IllegalStateException("garage_state control row is missing — Flyway V3 seeds it");
    }
}
