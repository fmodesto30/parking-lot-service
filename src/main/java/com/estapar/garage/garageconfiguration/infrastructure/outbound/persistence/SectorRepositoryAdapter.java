package com.estapar.garage.garageconfiguration.infrastructure.outbound.persistence;

import com.estapar.garage.garageconfiguration.domain.Sector;
import com.estapar.garage.garageconfiguration.domain.SectorRepository;
import com.estapar.garage.shared.domain.Money;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SectorRepositoryAdapter implements SectorRepository {

    private final SpringDataSectorRepository repository;

    public SectorRepositoryAdapter(SpringDataSectorRepository repository) {
        this.repository = repository;
    }

    @Override
    public Sector save(Sector sector) {
        var entity = repository
                .findById(sector.code())
                .map(existing -> {
                    existing.setBasePrice(sector.basePrice().amount());
                    existing.setMaxCapacity(sector.maxCapacity());
                    return existing;
                })
                .orElseGet(() ->
                        new SectorJpaEntity(sector.code(), sector.basePrice().amount(), sector.maxCapacity()));
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<Sector> findByCode(String code) {
        return repository.findById(code).map(this::toDomain);
    }

    @Override
    public List<Sector> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByCode(String code) {
        return repository.existsById(code);
    }

    private Sector toDomain(SectorJpaEntity entity) {
        return new Sector(entity.getCode(), Money.of(entity.getBasePrice()), entity.getMaxCapacity());
    }
}
