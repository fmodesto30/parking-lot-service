package com.estapar.garage.parking;

import static org.assertj.core.api.Assertions.assertThat;

import com.estapar.garage.parking.application.EventOutcome;
import com.estapar.garage.parking.application.HandleEntryEventUseCase;
import com.estapar.garage.parking.application.event.EntryEvent;
import com.estapar.garage.shared.domain.LicensePlate;
import com.estapar.garage.support.GarageTestFixtures;
import com.estapar.garage.support.MySqlIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The last global slot under contention: with capacity 1, N simultaneous ENTRYs for distinct
 * plates must admit exactly one vehicle and leave the counter at exactly 1 (spec 06 §concurrency).
 * A start barrier maximizes the race; no sleep-based coordination.
 */
class HandleEntryEventConcurrencyIT extends MySqlIntegrationTest {

    private static final int THREADS = 8;

    @Autowired
    private HandleEntryEventUseCase useCase;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        new GarageTestFixtures(jdbc).reset(1, 0);
    }

    @Test
    void onlyOneVehicleTakesTheLastGlobalSlot() throws Exception {
        var barrier = new CountDownLatch(1);
        var successes = new CopyOnWriteArrayList<EventOutcome>();
        var rejections = new CopyOnWriteArrayList<Throwable>();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, THREADS)
                .mapToObj(i -> (Callable<Void>) () -> {
                    barrier.await();
                    try {
                        var outcome = useCase.execute(new EntryEvent(
                                new LicensePlate("CAR" + String.format("%04d", i)),
                                Instant.parse("2026-07-13T12:00:00Z")));
                        successes.add(outcome);
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
        assertThat(jdbc.queryForObject("SELECT active_vehicle_count FROM garage_state WHERE id=1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parking_session WHERE status='ENTERED'", Long.class))
                .isEqualTo(1L);
    }
}
