package com.estapar.garage.parking;

import static org.assertj.core.api.Assertions.assertThat;

import com.estapar.garage.parking.application.EventOutcome;
import com.estapar.garage.parking.application.HandleEntryEventUseCase;
import com.estapar.garage.parking.application.HandleParkedEventUseCase;
import com.estapar.garage.parking.application.event.EntryEvent;
import com.estapar.garage.parking.application.event.ParkedEvent;
import com.estapar.garage.shared.domain.Coordinates;
import com.estapar.garage.shared.domain.LicensePlate;
import com.estapar.garage.support.GarageTestFixtures;
import com.estapar.garage.support.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Same spot under contention: several ENTERED vehicles race to PARK on the identical
 * coordinates. The spot's pessimistic lock must let exactly one occupy it; the rest are
 * rejected, and the spot ends up occupied by a single session (spec 06 §concurrency).
 */
class HandleParkedEventConcurrencyIT extends MySqlIntegrationTest {

    private static final int THREADS = 6;

    @Autowired
    private HandleEntryEventUseCase entryUseCase;

    @Autowired
    private HandleParkedEventUseCase parkedUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    private Coordinates targetSpot;

    @BeforeEach
    void setUp() {
        var fixtures = new GarageTestFixtures(jdbc);
        fixtures.reset(10, 20);
        double[] c = fixtures.spotCoordinates("A", 1);
        targetSpot = new Coordinates(BigDecimal.valueOf(c[0]), BigDecimal.valueOf(c[1]));
        for (int i = 0; i < THREADS; i++) {
            entryUseCase.execute(new EntryEvent(
                    new LicensePlate("CAR" + String.format("%04d", i)), Instant.parse("2026-07-13T12:00:00Z")));
        }
    }

    @Test
    void onlyOneVehicleOccupiesTheContestedSpot() throws Exception {
        var barrier = new CountDownLatch(1);
        var successes = new CopyOnWriteArrayList<EventOutcome>();
        var rejections = new CopyOnWriteArrayList<Throwable>();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        List<Callable<Void>> tasks = IntStream.range(0, THREADS)
                .mapToObj(i -> (Callable<Void>) () -> {
                    barrier.await();
                    try {
                        successes.add(parkedUseCase.execute(
                                new ParkedEvent(new LicensePlate("CAR" + String.format("%04d", i)), targetSpot)));
                    } catch (RuntimeException e) {
                        rejections.add(e);
                    }
                    return null;
                })
                .toList();

        List<Future<Void>> futures = tasks.stream().map(pool::submit).toList();
        barrier.countDown();
        for (Future<Void> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertThat(successes).containsExactly(EventOutcome.PROCESSED);
        assertThat(rejections).hasSize(THREADS - 1);
        assertThat(jdbc.queryForObject("SELECT status FROM parking_spot WHERE external_id=1", String.class))
                .isEqualTo("OCCUPIED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM parking_session WHERE status='PARKED' AND spot_id="
                                + "(SELECT id FROM parking_spot WHERE external_id=1)",
                        Long.class))
                .isEqualTo(1L);
    }
}
