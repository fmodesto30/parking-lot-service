package com.estapar.garage.garageconfiguration.infrastructure.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataSectorRepository extends JpaRepository<SectorJpaEntity, String> {}
