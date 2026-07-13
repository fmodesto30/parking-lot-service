package com.estapar.garage.revenue.infrastructure.outbound.persistence;

import com.estapar.garage.revenue.domain.DuplicateChargeException;
import com.estapar.garage.revenue.domain.ParkingCharge;
import com.estapar.garage.revenue.domain.ParkingChargeRepository;
import com.estapar.garage.shared.domain.Money;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class ParkingChargeRepositoryAdapter implements ParkingChargeRepository {

    private final SpringDataParkingChargeRepository repository;

    public ParkingChargeRepositoryAdapter(SpringDataParkingChargeRepository repository) {
        this.repository = repository;
    }

    @Override
    public ParkingCharge add(ParkingCharge charge) {
        try {
            var saved = repository.saveAndFlush(new ParkingChargeJpaEntity(
                    charge.sessionId(),
                    charge.sectorCode(),
                    charge.amount().amount(),
                    charge.amount().currency(),
                    charge.chargedAt()));
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // unique (session_id): the last line of defense against double billing (ADR-003/004)
            throw new DuplicateChargeException(charge.sessionId());
        }
    }

    @Override
    public Money sumBySectorAndPeriod(String sectorCode, Instant from, Instant to) {
        return Money.of(repository.sumBySectorAndPeriod(sectorCode, from, to));
    }

    private ParkingCharge toDomain(ParkingChargeJpaEntity entity) {
        return new ParkingCharge(
                entity.getId(),
                entity.getSessionId(),
                entity.getSectorCode(),
                Money.of(entity.getAmount()),
                entity.getChargedAt());
    }
}
