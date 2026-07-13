package com.estapar.garage.parking.infrastructure.outbound.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SpringDataGarageStateRepository extends JpaRepository<GarageStateJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GarageStateJpaEntity g where g.id = 1")
    Optional<GarageStateJpaEntity> lockSingleton();
}
