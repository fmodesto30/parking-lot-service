package com.estapar.garage.webhook.application;

import com.estapar.garage.garageconfiguration.application.GarageReadiness;
import com.estapar.garage.garageconfiguration.domain.exception.GarageNotReadyException;
import com.estapar.garage.parking.application.EventOutcome;
import com.estapar.garage.parking.application.HandleEntryEventUseCase;
import com.estapar.garage.parking.application.HandleExitEventUseCase;
import com.estapar.garage.parking.application.HandleParkedEventUseCase;
import com.estapar.garage.parking.application.event.EntryEvent;
import com.estapar.garage.parking.application.event.ExitEvent;
import com.estapar.garage.parking.application.event.GarageEvent;
import com.estapar.garage.parking.application.event.ParkedEvent;
import com.estapar.garage.shared.domain.DomainException;
import com.estapar.garage.shared.observability.GarageMetrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Dispatches a parsed event to the right use case via pattern matching. Rejects everything with
 * 503 until the garage configuration is loaded (spec 02 R6) — the simulator only emits events
 * after {@code GET /garage}, but a restart could momentarily race that window.
 */
@Service
public class WebhookEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventProcessor.class);

    private final HandleEntryEventUseCase handleEntry;
    private final HandleParkedEventUseCase handleParked;
    private final HandleExitEventUseCase handleExit;
    private final GarageReadiness readiness;
    private final GarageMetrics metrics;

    public WebhookEventProcessor(
            HandleEntryEventUseCase handleEntry,
            HandleParkedEventUseCase handleParked,
            HandleExitEventUseCase handleExit,
            GarageReadiness readiness,
            GarageMetrics metrics) {
        this.handleEntry = handleEntry;
        this.handleParked = handleParked;
        this.handleExit = handleExit;
        this.readiness = readiness;
        this.metrics = metrics;
    }

    public EventOutcome process(GarageEvent event) {
        if (!readiness.isReady()) {
            throw new GarageNotReadyException();
        }
        log.info(
                "webhook_event_received type={} plate={}",
                event.type(),
                event.plate().masked());
        Timer.Sample sample = metrics.startProcessing();
        try {
            EventOutcome outcome = dispatch(event);
            metrics.eventProcessed(event.type(), outcome);
            log.info("webhook_event_processed type={} outcome={}", event.type(), outcome);
            return outcome;
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            metrics.concurrencyConflict(event.type());
            metrics.eventRejected(event.type(), "ConcurrencyConflict");
            throw e;
        } catch (DomainException e) {
            log.warn(
                    "webhook_event_rejected type={} reason={}",
                    event.type(),
                    e.getClass().getSimpleName());
            metrics.eventRejected(event.type(), e.getClass().getSimpleName());
            throw e;
        } finally {
            metrics.recordProcessing(sample, event.type());
        }
    }

    private EventOutcome dispatch(GarageEvent event) {
        return switch (event) {
            case EntryEvent entry -> handleEntry.execute(entry);
            case ParkedEvent parked -> handleParked.execute(parked);
            case ExitEvent exit -> handleExit.execute(exit);
        };
    }
}
