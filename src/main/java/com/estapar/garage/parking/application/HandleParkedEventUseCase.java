package com.estapar.garage.parking.application;

import com.estapar.garage.garageconfiguration.domain.Sector;
import com.estapar.garage.garageconfiguration.domain.SectorRepository;
import com.estapar.garage.garageconfiguration.domain.exception.SectorNotFoundException;
import com.estapar.garage.parking.application.event.ParkedEvent;
import com.estapar.garage.parking.domain.ParkingSession;
import com.estapar.garage.parking.domain.ParkingSessionRepository;
import com.estapar.garage.parking.domain.ParkingSpot;
import com.estapar.garage.parking.domain.ParkingSpotRepository;
import com.estapar.garage.parking.domain.exception.ActiveSessionNotFoundException;
import com.estapar.garage.parking.domain.exception.ParkingSpotNotFoundException;
import com.estapar.garage.parking.domain.exception.SectorFullException;
import com.estapar.garage.pricing.domain.AppliedPrice;
import com.estapar.garage.pricing.domain.OccupancyPricingPolicy;
import com.estapar.garage.pricing.domain.OccupancyRate;
import com.estapar.garage.shared.application.FingerprintHasher;
import com.estapar.garage.shared.application.ProcessedEventRegistry;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PARKED (spec 02 R2/R3): the vehicle takes a physical spot; this is the first point where the
 * sector is known, so the dynamic price is computed and frozen here (ADR-002).
 *
 * <p>Lock order fingerprint → session → spot (ADR-003): the active session is locked first,
 * then the target spot, so a spot's availability check and its occupation are atomic against a
 * competing PARKED for the same coordinates. Occupancy — and therefore price — is measured
 * <em>before</em> the new vehicle is counted.
 */
@Service
public class HandleParkedEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandleParkedEventUseCase.class);

    private final ParkingSessionRepository sessionRepository;
    private final ParkingSpotRepository spotRepository;
    private final SectorRepository sectorRepository;
    private final OccupancyPricingPolicy pricingPolicy;
    private final ProcessedEventRegistry processedEvents;
    private final FingerprintHasher fingerprintHasher;
    private final Clock clock;

    public HandleParkedEventUseCase(
            ParkingSessionRepository sessionRepository,
            ParkingSpotRepository spotRepository,
            SectorRepository sectorRepository,
            OccupancyPricingPolicy pricingPolicy,
            ProcessedEventRegistry processedEvents,
            FingerprintHasher fingerprintHasher,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.spotRepository = spotRepository;
        this.sectorRepository = sectorRepository;
        this.pricingPolicy = pricingPolicy;
        this.processedEvents = processedEvents;
        this.fingerprintHasher = fingerprintHasher;
        this.clock = clock;
    }

    @Transactional
    public EventOutcome execute(ParkedEvent event) {
        String fingerprint = fingerprintHasher.hash(event.fingerprintSeed());
        if (!processedEvents.register(fingerprint, event.type(), event.plate().value(), clock.instant(), null)) {
            log.info(
                    "webhook_event_duplicate type=PARKED plate={}",
                    event.plate().masked());
            return EventOutcome.DUPLICATE;
        }

        ParkingSession session = sessionRepository
                .lockActiveByPlate(event.plate())
                .orElseThrow(() -> new ActiveSessionNotFoundException(event.plate()));

        ParkingSpot spot = spotRepository
                .lockByCoordinates(event.coordinates())
                .orElseThrow(() -> new ParkingSpotNotFoundException(event.coordinates()));

        Sector sector = sectorRepository
                .findByCode(spot.sectorCode())
                .orElseThrow(() -> new SectorNotFoundException(spot.sectorCode()));

        long occupiedBefore = spotRepository.countOccupiedBySector(sector.code());
        if (new OccupancyRate(occupiedBefore, sector.maxCapacity()).isFull()) {
            throw new SectorFullException(sector.code());
        }

        AppliedPrice appliedPrice = pricingPolicy.calculate(sector.basePrice(), occupiedBefore, sector.maxCapacity());

        spot.occupy(session.id());
        spotRepository.save(spot);

        session.parkAt(spot.id(), sector.code(), appliedPrice, clock.instant());
        sessionRepository.save(session);

        log.info(
                "vehicle_parked sessionId={} sector={} spot={} occupancyBefore={}/{} multiplier={} hourly={}",
                session.id(),
                sector.code(),
                spot.externalId(),
                occupiedBefore,
                sector.maxCapacity(),
                appliedPrice.multiplier(),
                appliedPrice.effectiveHourlyPrice().amount());
        return EventOutcome.PROCESSED;
    }
}
