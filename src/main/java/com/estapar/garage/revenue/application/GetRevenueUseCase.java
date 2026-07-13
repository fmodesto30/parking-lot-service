package com.estapar.garage.revenue.application;

import com.estapar.garage.garageconfiguration.domain.SectorRepository;
import com.estapar.garage.garageconfiguration.domain.exception.SectorNotFoundException;
import com.estapar.garage.revenue.domain.ParkingChargeRepository;
import com.estapar.garage.shared.configuration.GarageProperties;
import com.estapar.garage.shared.domain.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revenue by sector and calendar date (spec 02 R5). The date is resolved in the configured
 * business timezone and converted to a half-open UTC instant range {@code [00:00, next 00:00)},
 * so charges are summed by their local business day regardless of UTC storage (ADR-005).
 */
@Service
public class GetRevenueUseCase {

    private final ParkingChargeRepository chargeRepository;
    private final SectorRepository sectorRepository;
    private final ZoneId businessZone;
    private final Clock clock;

    public GetRevenueUseCase(
            ParkingChargeRepository chargeRepository,
            SectorRepository sectorRepository,
            GarageProperties properties,
            Clock clock) {
        this.chargeRepository = chargeRepository;
        this.sectorRepository = sectorRepository;
        this.businessZone = properties.businessZone();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RevenueView execute(String sectorCode, LocalDate date) {
        if (!sectorRepository.existsByCode(sectorCode)) {
            throw new SectorNotFoundException(sectorCode);
        }
        Instant from = date.atStartOfDay(businessZone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(businessZone).toInstant();
        Money amount = chargeRepository.sumBySectorAndPeriod(sectorCode, from, to);
        return new RevenueView(amount, clock.instant());
    }
}
