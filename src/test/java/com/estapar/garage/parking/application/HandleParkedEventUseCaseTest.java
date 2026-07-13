package com.estapar.garage.parking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estapar.garage.garageconfiguration.domain.Sector;
import com.estapar.garage.garageconfiguration.domain.SectorRepository;
import com.estapar.garage.parking.application.event.ParkedEvent;
import com.estapar.garage.parking.domain.ParkingSession;
import com.estapar.garage.parking.domain.ParkingSessionRepository;
import com.estapar.garage.parking.domain.ParkingSpot;
import com.estapar.garage.parking.domain.ParkingSpotRepository;
import com.estapar.garage.parking.domain.SpotStatus;
import com.estapar.garage.parking.domain.exception.ActiveSessionNotFoundException;
import com.estapar.garage.parking.domain.exception.ParkingSpotNotFoundException;
import com.estapar.garage.parking.domain.exception.ParkingSpotOccupiedException;
import com.estapar.garage.parking.domain.exception.SectorFullException;
import com.estapar.garage.pricing.domain.TieredOccupancyPricingPolicy;
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

class HandleParkedEventUseCaseTest {

    private static final LicensePlate PLATE = new LicensePlate("ZUL0001");
    private static final Coordinates COORD =
            new Coordinates(new BigDecimal("-23.561684"), new BigDecimal("-46.655981"));

    private ParkingSessionRepository sessionRepository;
    private ParkingSpotRepository spotRepository;
    private SectorRepository sectorRepository;
    private ProcessedEventRegistry processedEvents;
    private HandleParkedEventUseCase useCase;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(ParkingSessionRepository.class);
        spotRepository = mock(ParkingSpotRepository.class);
        sectorRepository = mock(SectorRepository.class);
        processedEvents = mock(ProcessedEventRegistry.class);
        var clock = Clock.fixed(Instant.parse("2026-07-13T12:05:00Z"), ZoneOffset.UTC);
        useCase = new HandleParkedEventUseCase(
                sessionRepository,
                spotRepository,
                sectorRepository,
                new TieredOccupancyPricingPolicy(),
                processedEvents,
                new FingerprintHasher(),
                clock);
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(spotRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ParkedEvent event() {
        return new ParkedEvent(PLATE, COORD);
    }

    private ParkingSession enteredSession() {
        return ParkingSession.restore(
                42L,
                PLATE,
                com.estapar.garage.parking.domain.SessionStatus.ENTERED,
                Instant.parse("2026-07-13T12:00:00Z"),
                null,
                null,
                null,
                null,
                null);
    }

    private ParkingSpot availableSpot() {
        return new ParkingSpot(7L, 7L, "A", COORD, SpotStatus.AVAILABLE, null);
    }

    private void fresh() {
        when(processedEvents.register(any(), any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void freezesPriceAtOccupancyBandBeforeOccupyingSpot() {
        fresh();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.of(enteredSession()));
        when(spotRepository.lockByCoordinates(COORD)).thenReturn(Optional.of(availableSpot()));
        when(sectorRepository.findByCode("A")).thenReturn(Optional.of(new Sector("A", Money.of("40.50"), 10)));
        when(spotRepository.countOccupiedBySector("A")).thenReturn(2L); // 20% → 0.90 band

        var outcome = useCase.execute(event());

        assertThat(outcome).isEqualTo(EventOutcome.PROCESSED);
        var savedSession = ArgumentCaptor.forClass(ParkingSession.class);
        verify(sessionRepository).save(savedSession.capture());
        var price = savedSession.getValue().appliedPrice();
        assertThat(price.multiplier()).isEqualByComparingTo("0.90");
        assertThat(price.effectiveHourlyPrice().amount()).isEqualByComparingTo("36.45");
        assertThat(savedSession.getValue().status()).isEqualTo(com.estapar.garage.parking.domain.SessionStatus.PARKED);
    }

    @Test
    void shortCircuitsOnDuplicate() {
        when(processedEvents.register(any(), any(), any(), any(), any())).thenReturn(false);

        assertThat(useCase.execute(event())).isEqualTo(EventOutcome.DUPLICATE);
        verify(sessionRepository, never()).lockActiveByPlate(any());
    }

    @Test
    void rejectsParkedWithoutActiveSession() {
        fresh();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(event())).isInstanceOf(ActiveSessionNotFoundException.class);
    }

    @Test
    void rejectsUnknownSpotCoordinates() {
        fresh();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.of(enteredSession()));
        when(spotRepository.lockByCoordinates(COORD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(event())).isInstanceOf(ParkingSpotNotFoundException.class);
    }

    @Test
    void rejectsAlreadyOccupiedSpot() {
        fresh();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.of(enteredSession()));
        var occupied = new ParkingSpot(7L, 7L, "A", COORD, SpotStatus.OCCUPIED, 99L);
        when(spotRepository.lockByCoordinates(COORD)).thenReturn(Optional.of(occupied));
        when(sectorRepository.findByCode("A")).thenReturn(Optional.of(new Sector("A", Money.of("40.50"), 10)));
        when(spotRepository.countOccupiedBySector("A")).thenReturn(1L);

        assertThatThrownBy(() -> useCase.execute(event())).isInstanceOf(ParkingSpotOccupiedException.class);
    }

    @Test
    void closesSectorAtFullOccupancy() {
        fresh();
        when(sessionRepository.lockActiveByPlate(PLATE)).thenReturn(Optional.of(enteredSession()));
        when(spotRepository.lockByCoordinates(COORD)).thenReturn(Optional.of(availableSpot()));
        when(sectorRepository.findByCode("A")).thenReturn(Optional.of(new Sector("A", Money.of("40.50"), 10)));
        when(spotRepository.countOccupiedBySector("A")).thenReturn(10L); // 100%

        assertThatThrownBy(() -> useCase.execute(event())).isInstanceOf(SectorFullException.class);
        verify(spotRepository, never()).save(any());
    }
}
