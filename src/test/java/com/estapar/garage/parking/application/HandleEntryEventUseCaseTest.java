package com.estapar.garage.parking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estapar.garage.parking.application.event.EntryEvent;
import com.estapar.garage.parking.domain.GarageState;
import com.estapar.garage.parking.domain.GarageStateRepository;
import com.estapar.garage.parking.domain.ParkingSession;
import com.estapar.garage.parking.domain.ParkingSessionRepository;
import com.estapar.garage.parking.domain.exception.ActiveSessionAlreadyExistsException;
import com.estapar.garage.parking.domain.exception.GarageFullException;
import com.estapar.garage.shared.application.FingerprintHasher;
import com.estapar.garage.shared.application.ProcessedEventRegistry;
import com.estapar.garage.shared.domain.LicensePlate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit behavior of ENTRY: idempotency short-circuit, conflict on an active session, global
 * capacity rejection, and the happy path. Repositories/registry are mocked — the concurrency
 * behavior is proven separately against real MySQL in {@code HandleEntryEventConcurrencyIT}.
 */
class HandleEntryEventUseCaseTest {

    private static final Instant ENTRY_TIME = Instant.parse("2026-07-13T12:00:00Z");
    private static final LicensePlate PLATE = new LicensePlate("ZUL0001");

    private ParkingSessionRepository sessionRepository;
    private GarageStateRepository garageStateRepository;
    private ProcessedEventRegistry processedEvents;
    private HandleEntryEventUseCase useCase;

    @BeforeEach
    void setUp() {
        sessionRepository = org.mockito.Mockito.mock(ParkingSessionRepository.class);
        garageStateRepository = org.mockito.Mockito.mock(GarageStateRepository.class);
        processedEvents = org.mockito.Mockito.mock(ProcessedEventRegistry.class);
        var clock = Clock.fixed(Instant.parse("2026-07-13T12:00:05Z"), ZoneOffset.UTC);
        useCase = new HandleEntryEventUseCase(
                sessionRepository, garageStateRepository, processedEvents, new FingerprintHasher(), clock);
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private EntryEvent event() {
        return new EntryEvent(PLATE, ENTRY_TIME);
    }

    private void freshEvent() {
        when(processedEvents.register(any(), any(), any(), any(), isNull())).thenReturn(true);
    }

    @Test
    void shouldRegisterSessionAndConsumeGlobalCapacity() {
        freshEvent();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.empty());
        var garage = new GarageState(1, 10, 3, null);
        when(garageStateRepository.lock()).thenReturn(garage);

        var outcome = useCase.execute(event());

        assertThat(outcome).isEqualTo(EventOutcome.PROCESSED);
        assertThat(garage.activeVehicleCount()).isEqualTo(4);
        var saved = ArgumentCaptor.forClass(ParkingSession.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().licensePlate()).isEqualTo(PLATE);
        verify(garageStateRepository).save(garage);
    }

    @Test
    void shouldShortCircuitOnDuplicateWithoutTouchingState() {
        when(processedEvents.register(any(), any(), any(), any(), isNull())).thenReturn(false);

        var outcome = useCase.execute(event());

        assertThat(outcome).isEqualTo(EventOutcome.DUPLICATE);
        verify(garageStateRepository, never()).lock();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void shouldRejectEntryWhenPlateHasActiveSession() {
        freshEvent();
        when(sessionRepository.lockActiveByPlate(PLATE))
                .thenReturn(Optional.of(ParkingSession.enter(PLATE, ENTRY_TIME)));

        assertThatThrownBy(() -> useCase.execute(event())).isInstanceOf(ActiveSessionAlreadyExistsException.class);
        verify(garageStateRepository, never()).lock();
    }

    @Test
    void shouldRejectEntryWhenGarageIsFull() {
        freshEvent();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.empty());
        when(garageStateRepository.lock()).thenReturn(new GarageState(1, 2, 2, null));

        assertThatThrownBy(() -> useCase.execute(event())).isInstanceOf(GarageFullException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void registersFingerprintWithPlainPlateValueForCorrelation() {
        freshEvent();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.empty());
        when(garageStateRepository.lock()).thenReturn(new GarageState(1, 10, 0, null));

        useCase.execute(event());

        verify(processedEvents).register(any(), eq("ENTRY"), eq("ZUL0001"), any(), isNull());
    }
}
