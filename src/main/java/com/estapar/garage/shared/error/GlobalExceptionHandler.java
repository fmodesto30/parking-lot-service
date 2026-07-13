package com.estapar.garage.shared.error;

import com.estapar.garage.garageconfiguration.domain.exception.GarageNotReadyException;
import com.estapar.garage.garageconfiguration.domain.exception.SectorNotFoundException;
import com.estapar.garage.parking.domain.exception.ActiveSessionAlreadyExistsException;
import com.estapar.garage.parking.domain.exception.ActiveSessionNotFoundException;
import com.estapar.garage.parking.domain.exception.GarageFullException;
import com.estapar.garage.parking.domain.exception.InvalidExitTimeException;
import com.estapar.garage.parking.domain.exception.InvalidSessionTransitionException;
import com.estapar.garage.parking.domain.exception.ParkingSpotNotFoundException;
import com.estapar.garage.parking.domain.exception.ParkingSpotOccupiedException;
import com.estapar.garage.parking.domain.exception.SectorFullException;
import com.estapar.garage.revenue.domain.DuplicateChargeException;
import com.estapar.garage.webhook.application.InvalidWebhookEventException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps domain and framework exceptions to RFC-7807 {@link ProblemDetail} responses, never
 * leaking a stack trace (spec 07). Business rejections are logged at WARN with their
 * correlation id; only genuinely unexpected failures are ERROR.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://estapar.local/problems/";

    // --- 400 Bad Request -----------------------------------------------------------------

    @ExceptionHandler(InvalidWebhookEventException.class)
    public ProblemDetail onInvalidWebhookEvent(InvalidWebhookEventException e, HttpServletRequest request) {
        return warn(HttpStatus.BAD_REQUEST, "invalid-webhook-event", "Invalid webhook event", e.getMessage(), request);
    }

    @ExceptionHandler(InvalidExitTimeException.class)
    public ProblemDetail onInvalidExitTime(InvalidExitTimeException e, HttpServletRequest request) {
        return warn(HttpStatus.BAD_REQUEST, "invalid-exit-time", "Invalid exit time", e.getMessage(), request);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        IllegalArgumentException.class
    })
    public ProblemDetail onMalformedRequest(Exception e, HttpServletRequest request) {
        return warn(
                HttpStatus.BAD_REQUEST,
                "malformed-request",
                "Malformed request",
                "The request could not be read or is missing required fields.",
                request);
    }

    // --- 404 Not Found -------------------------------------------------------------------

    @ExceptionHandler(ParkingSpotNotFoundException.class)
    public ProblemDetail onSpotNotFound(ParkingSpotNotFoundException e, HttpServletRequest request) {
        return warn(HttpStatus.NOT_FOUND, "parking-spot-not-found", "Parking spot not found", e.getMessage(), request);
    }

    @ExceptionHandler(ActiveSessionNotFoundException.class)
    public ProblemDetail onSessionNotFound(ActiveSessionNotFoundException e, HttpServletRequest request) {
        return warn(
                HttpStatus.NOT_FOUND, "active-session-not-found", "Active session not found", e.getMessage(), request);
    }

    @ExceptionHandler(SectorNotFoundException.class)
    public ProblemDetail onSectorNotFound(SectorNotFoundException e, HttpServletRequest request) {
        return warn(HttpStatus.NOT_FOUND, "sector-not-found", "Sector not found", e.getMessage(), request);
    }

    // --- 409 Conflict --------------------------------------------------------------------

    @ExceptionHandler(GarageFullException.class)
    public ProblemDetail onGarageFull(GarageFullException e, HttpServletRequest request) {
        return warn(HttpStatus.CONFLICT, "garage-full", "Garage is full", e.getMessage(), request);
    }

    @ExceptionHandler(SectorFullException.class)
    public ProblemDetail onSectorFull(SectorFullException e, HttpServletRequest request) {
        return warn(HttpStatus.CONFLICT, "sector-full", "Sector is full", e.getMessage(), request);
    }

    @ExceptionHandler(ParkingSpotOccupiedException.class)
    public ProblemDetail onSpotOccupied(ParkingSpotOccupiedException e, HttpServletRequest request) {
        return warn(HttpStatus.CONFLICT, "parking-spot-occupied", "Parking spot occupied", e.getMessage(), request);
    }

    @ExceptionHandler(ActiveSessionAlreadyExistsException.class)
    public ProblemDetail onSessionExists(ActiveSessionAlreadyExistsException e, HttpServletRequest request) {
        return warn(
                HttpStatus.CONFLICT, "active-session-exists", "Active session already exists", e.getMessage(), request);
    }

    @ExceptionHandler(InvalidSessionTransitionException.class)
    public ProblemDetail onInvalidTransition(InvalidSessionTransitionException e, HttpServletRequest request) {
        return warn(
                HttpStatus.CONFLICT,
                "invalid-session-transition",
                "Invalid session transition",
                e.getMessage(),
                request);
    }

    @ExceptionHandler(DuplicateChargeException.class)
    public ProblemDetail onDuplicateCharge(DuplicateChargeException e, HttpServletRequest request) {
        return warn(HttpStatus.CONFLICT, "duplicate-charge", "Duplicate charge", e.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail onDataIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        // Backstop for a unique-constraint race that slipped past the pre-checks (ADR-003/004).
        return warn(
                HttpStatus.CONFLICT,
                "conflicting-state",
                "Conflicting state",
                "The operation conflicts with the current state of the resource.",
                request);
    }

    // --- 503 Service Unavailable ---------------------------------------------------------

    @ExceptionHandler(GarageNotReadyException.class)
    public ProblemDetail onNotReady(GarageNotReadyException e, HttpServletRequest request) {
        return warn(HttpStatus.SERVICE_UNAVAILABLE, "garage-not-ready", "Garage not ready", e.getMessage(), request);
    }

    // --- 500 Internal Server Error -------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception e, HttpServletRequest request) {
        log.error(
                "unexpected_error path={} type={}",
                request.getRequestURI(),
                e.getClass().getSimpleName(),
                e);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal server error",
                "An unexpected error occurred.",
                request);
    }

    private ProblemDetail warn(
            HttpStatus status, String slug, String title, String detail, HttpServletRequest request) {
        log.warn("request_rejected status={} type={} path={}", status.value(), slug, request.getRequestURI());
        return build(status, slug, title, detail, request);
    }

    private ProblemDetail build(
            HttpStatus status, String slug, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + slug));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", CorrelationIdProvider.current());
        return problem;
    }
}
