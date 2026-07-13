package com.estapar.garage.parking.infrastructure.outbound.persistence;

import com.estapar.garage.parking.domain.SpotStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataParkingSpotRepository extends JpaRepository<ParkingSpotJpaEntity, Long> {

    Optional<ParkingSpotJpaEntity> findByExternalId(long externalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSpotJpaEntity s where s.latitude = :latitude and s.longitude = :longitude")
    Optional<ParkingSpotJpaEntity> lockByCoordinates(
            @Param("latitude") BigDecimal latitude, @Param("longitude") BigDecimal longitude);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSpotJpaEntity s where s.id = :id")
    Optional<ParkingSpotJpaEntity> lockById(@Param("id") long id);

    long countBySectorCodeAndStatus(String sectorCode, SpotStatus status);
}
