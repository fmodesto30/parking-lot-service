package com.estapar.garage.parking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estapar.garage.parking.application.event.ExitEvent;
import com.estapar.garage.parking.domain.GarageState;
import com.estapar.garage.parking.domain.GarageStateRepository;
import com.estapar.garage.parking.domain.ParkingSession;
import com.estapar.garage.parking.domain.ParkingSessionRepository;
import com.estapar.garage.parking.domain.ParkingSpot;
import com.estapar.garage.parking.domain.ParkingSpotRepository;
import com.estapar.garage.parking.domain.SessionStatus;
import com.estapar.garage.parking.domain.SpotStatus;
import com.estapar.garage.parking.domain.exception.ActiveSessionNotFoundException;
import com.estapar.garage.parking.domain.exception.InvalidExitTimeException;
import com.estapar.garage.pricing.domain.AppliedPrice;
import com.estapar.garage.pricing.domain.BillingCalculator;
import com.estapar.garage.revenue.domain.ParkingCharge;
import com.estapar.garage.revenue.domain.ParkingChargeRepository;
import com.estapar.garage.shared.application.FingerprintHasher;
import com.estapar.garage.shared.application.ProcessedEventRegistry;
import com.estapar.garage.shared.domain.Coordinates;
import com.estapar.garage.shared.domain.LicensePlate;
import com.estapar.garage.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HandleExitEventUseCaseTest {

    private static final LicensePlate PLATE = new LicensePlate("ZUL0001");
    private static final Instant ENTRY = Instant.parse("2026-07-13T12:00:00Z");
    private static final Coordinates COORD =
            new Coordinates(new BigDecimal("-23.561684"), new BigDecimal("-46.655981"));

    private ParkingSessionRepository sessionRepository;
    private ParkingSpotRepository spotRepository;
    private GarageStateRepository garageStateRepository;
    private ParkingChargeRepository chargeRepository;
    private ProcessedEventRegistry processedEvents;
    private HandleExitEventUseCase useCase;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(ParkingSessionRepository.class);
        spotRepository = mock(ParkingSpotRepository.class);
        garageStateRepository = mock(GarageStateRepository.class);
        chargeRepository = mock(ParkingChargeRepository.class);
        processedEvents = mock(ProcessedEventRegistry.class);
        var clock = Clock.fixed(Instant.parse("2026-07-13T14:00:00Z"), ZoneOffset.UTC);
        useCase = new HandleExitEventUseCase(
                sessionRepository,
                spotRepository,
                garageStateRepository,
                chargeRepository,
                new BillingCalculator(),
                processedEvents,
                new FingerprintHasher(),
                clock);
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(spotRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(chargeRepository.add(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void fresh() {
        when(processedEvents.register(any(), any(), any(), any(), any())).thenReturn(true);
    }

    private ParkingSession parkedSession() {
        var price = new AppliedPrice(
                Money.of("40.50"), new BigDecimal("0.2000"), new BigDecimal("0.90"), Money.of("36.45"));
        return ParkingSession.restore(
                42L, PLATE, SessionStatus.PARKED, ENTRY, ENTRY.plusSeconds(60), null, 7L, "A", price);
    }

    private ParkingSession enteredOnlySession() {
        return ParkingSession.restore(43L, PLATE, SessionStatus.ENTERED, ENTRY, null, null, null, null, null);
    }

    private ExitEvent exitAfter(long minutes) {
        return new ExitEvent(PLATE, ENTRY.plus(java.time.Duration.ofMinutes(minutes)));
    }

    @Test
    void billsParkedStayReleasesSpotAndDecrementsCount() {
        fresh();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.of(parkedSession()));
        when(spotRepository.lockById(7L))
                .thenReturn(Optional.of(new ParkingSpot(7L, 7L, "A", COORD, SpotStatus.OCCUPIED, 42L)));
        var garage = new GarageState(1, 30, 5, null);
        when(garageStateRepository.lock()).thenReturn(garage);

        // 95 minutes → ceil(95/60) = 2 billable hours × 36.45 = 72.90
        var outcome = useCase.execute(exitAfter(95));

        assertThat(outcome).isEqualTo(EventOutcome.PROCESSED);
        var charge = ArgumentCaptor.forClass(ParkingCharge.class);
        verify(chargeRepository).add(charge.capture());
        assertThat(charge.getValue().amount().amount()).isEqualByComparingTo("72.90");
        assertThat(charge.getValue().sectorCode()).isEqualTo("A");
        var savedSpot = ArgumentCaptor.forClass(ParkingSpot.class);
        verify(spotRepository).save(savedSpot.capture());
        assertThat(savedSpot.getValue().status()).isEqualTo(SpotStatus.AVAILABLE);
        assertThat(garage.activeVehicleCount()).isEqualTo(4);
    }

    @Test
    void freeStayUnderGraceStillCreatesZeroCharge() {
        fresh();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.of(parkedSession()));
        when(spotRepository.lockById(7L))
                .thenReturn(Optional.of(new ParkingSpot(7L, 7L, "A", COORD, SpotStatus.OCCUPIED, 42L)));
        when(garageStateRepository.lock()).thenReturn(new GarageState(1, 30, 1, null));

        useCase.execute(exitAfter(20)); // ≤ 30 min

        var charge = ArgumentCaptor.forClass(ParkingCharge.class);
        verify(chargeRepository).add(charge.capture());
        assertThat(charge.getValue().amount().isZero()).isTrue();
    }

    @Test
    void vehicleThatNeverParkedExitsWithoutChargeOrSpotRelease() {
        fresh();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.of(enteredOnlySession()));
        when(garageStateRepository.lock()).thenReturn(new GarageState(1, 30, 3, null));

        var outcome = useCase.execute(exitAfter(10));

        assertThat(outcome).isEqualTo(EventOutcome.PROCESSED);
        verify(chargeRepository, never()).add(any());
        verify(spotRepository, never()).save(any());
    }

    @Test
    void shortCircuitsOnDuplicateExit() {
        when(processedEvents.register(any(), any(), any(), any(), any())).thenReturn(false);

        assertThat(useCase.execute(exitAfter(60))).isEqualTo(EventOutcome.DUPLICATE);
        verify(chargeRepository, never()).add(any());
        verify(garageStateRepository, never()).lock();
    }

    @Test
    void rejectsExitWithoutActiveSession() {
        fresh();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(exitAfter(60))).isInstanceOf(ActiveSessionNotFoundException.class);
    }

    @Test
    void rejectsExitBeforeEntry() {
        fresh();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.of(parkedSession()));

        assertThatThrownBy(() -> useCase.execute(new ExitEvent(PLATE, ENTRY.minusSeconds(1))))
                .isInstanceOf(InvalidExitTimeException.class);
        verify(chargeRepository, never()).add(any());
    }
}
