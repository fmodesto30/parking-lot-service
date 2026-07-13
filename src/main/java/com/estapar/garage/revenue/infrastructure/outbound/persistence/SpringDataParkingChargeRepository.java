package com.estapar.garage.revenue.infrastructure.outbound.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataParkingChargeRepository extends JpaRepository<ParkingChargeJpaEntity, Long> {

    @Query("""
            select coalesce(sum(c.amount), 0)
            from ParkingChargeJpaEntity c
            where c.sectorCode = :sectorCode and c.chargedAt >= :from and c.chargedAt < :to
            """)
    BigDecimal sumBySectorAndPeriod(
            @Param("sectorCode") String sectorCode, @Param("from") Instant from, @Param("to") Instant to);

    Optional<ParkingChargeJpaEntity> findBySessionId(long sessionId);
}
