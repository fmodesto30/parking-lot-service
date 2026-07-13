package com.estapar.garage.parking.infrastructure.outbound.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataParkingSessionRepository extends JpaRepository<ParkingSessionJpaEntity, Long> {

    Optional<ParkingSessionJpaEntity> findByActivePlate(String activePlate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSessionJpaEntity s where s.activePlate = :plate")
    Optional<ParkingSessionJpaEntity> lockByActivePlate(@Param("plate") String plate);

    long countByActivePlateIsNotNull();
}
