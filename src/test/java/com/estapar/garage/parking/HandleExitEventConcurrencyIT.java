package com.estapar.garage.parking;

import static org.assertj.core.api.Assertions.assertThat;

import com.estapar.garage.parking.application.EventOutcome;
import com.estapar.garage.parking.application.HandleEntryEventUseCase;
import com.estapar.garage.parking.application.HandleExitEventUseCase;
import com.estapar.garage.parking.application.HandleParkedEventUseCase;
import com.estapar.garage.parking.application.event.EntryEvent;
import com.estapar.garage.parking.application.event.ExitEvent;
import com.estapar.garage.parking.application.event.ParkedEvent;
import com.estapar.garage.shared.domain.Coordinates;
import com.estapar.garage.shared.domain.LicensePlate;
import com.estapar.garage.support.GarageTestFixtures;
import com.estapar.garage.support.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
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
 * Double EXIT under contention: the same parked session is exited by several threads at once
 * (identical event, identical fingerprint). Exactly one charge is created, the spot is freed
 * once and the global counter drops by exactly one (spec 06 §concurrency).
 */
class HandleExitEventConcurrencyIT extends MySqlIntegrationTest {

    private static final int THREADS = 6;
    private static final Instant ENTRY = Instant.parse("2026-07-13T12:00:00Z");

    @Autowired
    private HandleEntryEventUseCase entryUseCase;

    @Autowired
    private HandleParkedEventUseCase parkedUseCase;

    @Autowired
    private HandleExitEventUseCase exitUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        var fixtures = new GarageTestFixtures(jdbc);
        fixtures.reset(10, 20);
        double[] c = fixtures.spotCoordinates("A", 1);
        var plate = new LicensePlate("SOLO001");
        entryUseCase.execute(new EntryEvent(plate, ENTRY));
        parkedUseCase.execute(
                new ParkedEvent(plate, new Coordinates(BigDecimal.valueOf(c[0]), BigDecimal.valueOf(c[1]))));
    }

    @Test
    void concurrentDuplicateExitChargesOnlyOnce() throws Exception {
        var exit = new ExitEvent(new LicensePlate("SOLO001"), ENTRY.plus(Duration.ofMinutes(90)));
        var barrier = new CountDownLatch(1);
        var outcomes = new CopyOnWriteArrayList<EventOutcome>();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        List<Callable<Void>> tasks = IntStream.range(0, THREADS)
                .mapToObj(i -> (Callable<Void>) () -> {
                    barrier.await();
                    try {
                        outcomes.add(exitUseCase.execute(exit));
                    } catch (RuntimeException ignored) {
                        // A losing thread may see a transient lock/rollback; the invariant is the DB state.
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

        assertThat(outcomes).contains(EventOutcome.PROCESSED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parking_charge", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT active_vehicle_count FROM garage_state WHERE id=1", Integer.class))
                .isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT status FROM parking_spot WHERE external_id=1", String.class))
                .isEqualTo("AVAILABLE");
    }
}
