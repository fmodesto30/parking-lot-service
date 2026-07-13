package com.estapar.garage.parking.application;

import com.estapar.garage.parking.application.event.ExitEvent;
import com.estapar.garage.parking.domain.GarageState;
import com.estapar.garage.parking.domain.GarageStateRepository;
import com.estapar.garage.parking.domain.ParkingSession;
import com.estapar.garage.parking.domain.ParkingSessionRepository;
import com.estapar.garage.parking.domain.ParkingSpot;
import com.estapar.garage.parking.domain.ParkingSpotRepository;
import com.estapar.garage.parking.domain.exception.ActiveSessionNotFoundException;
import com.estapar.garage.pricing.domain.BillingCalculator;
import com.estapar.garage.revenue.domain.ParkingCharge;
import com.estapar.garage.revenue.domain.ParkingChargeRepository;
import com.estapar.garage.shared.application.FingerprintHasher;
import com.estapar.garage.shared.application.ProcessedEventRegistry;
import com.estapar.garage.shared.domain.Money;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EXIT (spec 02 R4): closes the session, bills a parked stay from the frozen snapshot, releases
 * the spot and frees a unit of global capacity — all in one transaction.
 *
 * <p>Billing uses the price frozen at PARKED, never live occupancy. A vehicle that never parked
 * (ENTERED → EXITED) closes with no charge and no spot to release. The immutable
 * {@code parking_charge} row (unique {@code session_id}) is the last line of defense against
 * double billing. Lock order fingerprint → session → spot → garage_state (ADR-003).
 */
@Service
public class HandleExitEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandleExitEventUseCase.class);

    private final ParkingSessionRepository sessionRepository;
    private final ParkingSpotRepository spotRepository;
    private final GarageStateRepository garageStateRepository;
    private final ParkingChargeRepository chargeRepository;
    private final BillingCalculator billingCalculator;
    private final ProcessedEventRegistry processedEvents;
    private final FingerprintHasher fingerprintHasher;
    private final Clock clock;

    public HandleExitEventUseCase(
            ParkingSessionRepository sessionRepository,
            ParkingSpotRepository spotRepository,
            GarageStateRepository garageStateRepository,
            ParkingChargeRepository chargeRepository,
            BillingCalculator billingCalculator,
            ProcessedEventRegistry processedEvents,
            FingerprintHasher fingerprintHasher,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.spotRepository = spotRepository;
        this.garageStateRepository = garageStateRepository;
        this.chargeRepository = chargeRepository;
        this.billingCalculator = billingCalculator;
        this.processedEvents = processedEvents;
        this.fingerprintHasher = fingerprintHasher;
        this.clock = clock;
    }

    @Transactional
    public EventOutcome execute(ExitEvent event) {
        String fingerprint = fingerprintHasher.hash(event.fingerprintSeed());
        if (!processedEvents.register(fingerprint, event.type(), event.plate().value(), clock.instant(), null)) {
            log.info("webhook_event_duplicate type=EXIT plate={}", event.plate().masked());
            return EventOutcome.DUPLICATE;
        }

        ParkingSession session = sessionRepository
                .lockActiveByPlate(event.plate())
                .orElseThrow(() -> new ActiveSessionNotFoundException(event.plate()));

        session.exitAt(event.exitTime());

        if (session.wasParked()) {
            billAndReleaseSpot(session);
        }

        GarageState garage = garageStateRepository.lock();
        garage.releaseVehicle();
        garageStateRepository.save(garage);

        sessionRepository.save(session);
        log.info(
                "parking_session_completed sessionId={} plate={} parked={} activeVehicles={}",
                session.id(),
                event.plate().masked(),
                session.wasParked(),
                garage.activeVehicleCount());
        return EventOutcome.PROCESSED;
    }

    private void billAndReleaseSpot(ParkingSession session) {
        Duration stay = session.stayDuration();
        Money amount = billingCalculator.charge(stay, session.appliedPrice().effectiveHourlyPrice());
        chargeRepository.add(ParkingCharge.of(session.id(), session.sectorCode(), amount, session.exitTime()));

        ParkingSpot spot = spotRepository.lockById(session.spotId()).orElseThrow();
        spot.release();
        spotRepository.save(spot);

        log.info(
                "parking_charge_created sessionId={} sector={} billableHours={} amount={}",
                session.id(),
                session.sectorCode(),
                billingCalculator.billableHours(stay),
                amount.amount());
    }
}
