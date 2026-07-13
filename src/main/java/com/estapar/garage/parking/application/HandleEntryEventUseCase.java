package com.estapar.garage.parking.application;

import com.estapar.garage.parking.application.event.EntryEvent;
import com.estapar.garage.parking.domain.GarageState;
import com.estapar.garage.parking.domain.GarageStateRepository;
import com.estapar.garage.parking.domain.ParkingSession;
import com.estapar.garage.parking.domain.ParkingSessionRepository;
import com.estapar.garage.parking.domain.exception.ActiveSessionAlreadyExistsException;
import com.estapar.garage.shared.application.FingerprintHasher;
import com.estapar.garage.shared.application.ProcessedEventRegistry;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ENTRY (spec 02 R1): admits a vehicle through the single gate group, consuming global
 * capacity. No sector is known yet (ADR-002). Idempotent and concurrency-safe:
 *
 * <ol>
 *   <li>register the fingerprint first — a replay short-circuits with zero side effects;
 *   <li>lock the active-session row for the plate (also gap-locks its absence), rejecting a
 *       plate that already has an active session;
 *   <li>lock {@code garage_state} and admit — this serializes the single gate and enforces
 *       global capacity;
 *   <li>persist the {@code ENTERED} session (unique {@code active_plate} is the final backstop).
 * </ol>
 *
 * Lock order is session → garage_state, matching PARKED/EXIT (ADR-003) to avoid deadlocks.
 */
@Service
public class HandleEntryEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandleEntryEventUseCase.class);

    private final ParkingSessionRepository sessionRepository;
    private final GarageStateRepository garageStateRepository;
    private final ProcessedEventRegistry processedEvents;
    private final FingerprintHasher fingerprintHasher;
    private final Clock clock;

    public HandleEntryEventUseCase(
            ParkingSessionRepository sessionRepository,
            GarageStateRepository garageStateRepository,
            ProcessedEventRegistry processedEvents,
            FingerprintHasher fingerprintHasher,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.garageStateRepository = garageStateRepository;
        this.processedEvents = processedEvents;
        this.fingerprintHasher = fingerprintHasher;
        this.clock = clock;
    }

    @Transactional
    public EventOutcome execute(EntryEvent event) {
        String fingerprint = fingerprintHasher.hash(event.fingerprintSeed());
        if (!processedEvents.register(fingerprint, event.type(), event.plate().value(), clock.instant(), null)) {
            log.info(
                    "webhook_event_duplicate type=ENTRY plate={}", event.plate().masked());
            return EventOutcome.DUPLICATE;
        }

        if (sessionRepository.lockActiveByPlate(event.plate()).isPresent()) {
            throw new ActiveSessionAlreadyExistsException(event.plate());
        }

        GarageState garage = garageStateRepository.lock();
        garage.admitVehicle();
        garageStateRepository.save(garage);

        ParkingSession session = sessionRepository.save(ParkingSession.enter(event.plate(), event.entryTime()));
        log.info(
                "parking_session_created sessionId={} plate={} activeVehicles={}",
                session.id(),
                event.plate().masked(),
                garage.activeVehicleCount());
        return EventOutcome.PROCESSED;
    }
}
