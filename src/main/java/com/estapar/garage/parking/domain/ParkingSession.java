package com.estapar.garage.parking.domain;

import com.estapar.garage.parking.domain.exception.InvalidExitTimeException;
import com.estapar.garage.parking.domain.exception.InvalidSessionTransitionException;
import com.estapar.garage.pricing.domain.AppliedPrice;
import com.estapar.garage.shared.domain.LicensePlate;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A vehicle's stay, from gate entry to gate exit. State changes only through behavior methods
 * that enforce the transition table (spec 01):
 *
 * <pre>
 * ENTERED → PARKED → EXITED
 * ENTERED → EXITED            (left without parking; no pricing context)
 * </pre>
 */
public class ParkingSession {

    private final Long id;
    private final LicensePlate licensePlate;
    private SessionStatus status;
    private final Instant entryTime;
    private Instant parkedAt;
    private Instant exitTime;
    private Long spotId;
    private String sectorCode;
    private AppliedPrice appliedPrice;

    private ParkingSession(
            Long id,
            LicensePlate licensePlate,
            SessionStatus status,
            Instant entryTime,
            Instant parkedAt,
            Instant exitTime,
            Long spotId,
            String sectorCode,
            AppliedPrice appliedPrice) {
        this.id = id;
        this.licensePlate = Objects.requireNonNull(licensePlate, "licensePlate");
        this.status = Objects.requireNonNull(status, "status");
        this.entryTime = Objects.requireNonNull(entryTime, "entryTime");
        this.parkedAt = parkedAt;
        this.exitTime = exitTime;
        this.spotId = spotId;
        this.sectorCode = sectorCode;
        this.appliedPrice = appliedPrice;
    }

    /** A vehicle passed the gate: new active session, no spot, no sector, no price yet. */
    public static ParkingSession enter(LicensePlate plate, Instant entryTime) {
        return new ParkingSession(null, plate, SessionStatus.ENTERED, entryTime, null, null, null, null, null);
    }

    /** Rehydration from persistence — not a business operation. */
    public static ParkingSession restore(
            Long id,
            LicensePlate licensePlate,
            SessionStatus status,
            Instant entryTime,
            Instant parkedAt,
            Instant exitTime,
            Long spotId,
            String sectorCode,
            AppliedPrice appliedPrice) {
        return new ParkingSession(
                id, licensePlate, status, entryTime, parkedAt, exitTime, spotId, sectorCode, appliedPrice);
    }

    /** The vehicle took a spot; the sector's pricing decision is frozen here (ADR-002). */
    public void parkAt(long spotId, String sectorCode, AppliedPrice appliedPrice, Instant parkedAt) {
        if (status != SessionStatus.ENTERED) {
            throw new InvalidSessionTransitionException(status.name(), "PARK");
        }
        this.spotId = spotId;
        this.sectorCode = Objects.requireNonNull(sectorCode, "sectorCode");
        this.appliedPrice = Objects.requireNonNull(appliedPrice, "appliedPrice");
        this.parkedAt = Objects.requireNonNull(parkedAt, "parkedAt");
        this.status = SessionStatus.PARKED;
    }

    /** The vehicle left the garage. Allowed from ENTERED (never parked) and PARKED. */
    public void exitAt(Instant exitTime) {
        Objects.requireNonNull(exitTime, "exitTime");
        if (status == SessionStatus.EXITED) {
            throw new InvalidSessionTransitionException(status.name(), "EXIT");
        }
        if (exitTime.isBefore(entryTime)) {
            throw new InvalidExitTimeException(exitTime, entryTime);
        }
        this.exitTime = exitTime;
        this.status = SessionStatus.EXITED;
    }

    /** Total stay used for billing — event-embedded times, never delivery times. */
    public Duration stayDuration() {
        if (exitTime == null) {
            throw new InvalidSessionTransitionException(status.name(), "BILL_BEFORE_EXIT");
        }
        return Duration.between(entryTime, exitTime);
    }

    public boolean wasParked() {
        return spotId != null;
    }

    public boolean isActive() {
        return status != SessionStatus.EXITED;
    }

    public Long id() {
        return id;
    }

    public LicensePlate licensePlate() {
        return licensePlate;
    }

    public SessionStatus status() {
        return status;
    }

    public Instant entryTime() {
        return entryTime;
    }

    public Instant parkedAt() {
        return parkedAt;
    }

    public Instant exitTime() {
        return exitTime;
    }

    public Long spotId() {
        return spotId;
    }

    public String sectorCode() {
        return sectorCode;
    }

    public AppliedPrice appliedPrice() {
        return appliedPrice;
    }
}
