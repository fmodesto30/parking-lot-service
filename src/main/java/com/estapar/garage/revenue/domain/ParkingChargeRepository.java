package com.estapar.garage.revenue.domain;

import com.estapar.garage.shared.domain.Money;
import java.time.Instant;

/** Port: immutable charge persistence — insert and aggregate only, by design. */
public interface ParkingChargeRepository {

    /**
     * Inserts the charge. Throws {@link DuplicateChargeException} if the session already has
     * one (unique constraint — last line of defense against double billing).
     */
    ParkingCharge add(ParkingCharge charge);

    /** Sum of charges for the sector within {@code [from, to)}; zero when there are none. */
    Money sumBySectorAndPeriod(String sectorCode, Instant from, Instant to);
}
