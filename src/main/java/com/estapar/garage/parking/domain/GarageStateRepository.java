package com.estapar.garage.parking.domain;

/** Port: the single global-capacity row. {@link #lock()} acquires PESSIMISTIC_WRITE (ADR-003). */
public interface GarageStateRepository {

    /** Locks the singleton row for the current transaction. */
    GarageState lock();

    GarageState get();

    void save(GarageState state);
}
