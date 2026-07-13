package com.estapar.garage.parking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.estapar.garage.parking.domain.exception.GarageFullException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GarageStateTest {

    @Test
    void shouldRejectEntryWhenGarageHasNoAvailableCapacity() {
        var state = new GarageState(1, 2, 2, null);

        assertThatThrownBy(state::admitVehicle).isInstanceOf(GarageFullException.class);
    }

    @Test
    void shouldRejectEntryBeforeFirstSynchronization() {
        var state = new GarageState(1, 0, 0, null);

        assertThatThrownBy(state::admitVehicle).isInstanceOf(GarageFullException.class);
    }

    @Test
    void admitsUpToCapacityThenReleases() {
        var state = new GarageState(1, 2, 1, null);

        state.admitVehicle();
        assertThat(state.activeVehicleCount()).isEqualTo(2);

        state.releaseVehicle();
        assertThat(state.activeVehicleCount()).isEqualTo(1);
    }

    @Test
    void releaseNeverGoesBelowZero() {
        var state = new GarageState(1, 5, 0, null);

        state.releaseVehicle();

        assertThat(state.activeVehicleCount()).isZero();
    }

    @Test
    void synchronizeReplacesCapacityAndReconciledCount() {
        var state = new GarageState(1, 0, 0, null);
        var now = Instant.parse("2026-07-13T12:00:00Z");

        state.synchronize(30, 4, now);

        assertThat(state.totalCapacity()).isEqualTo(30);
        assertThat(state.activeVehicleCount()).isEqualTo(4);
        assertThat(state.lastSynchronizedAt()).isEqualTo(now);
    }
}
