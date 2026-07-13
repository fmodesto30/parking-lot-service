package com.estapar.garage.garageconfiguration.domain;

import java.util.List;
import java.util.Optional;

/** Port: sector reference data. */
public interface SectorRepository {

    Sector save(Sector sector);

    Optional<Sector> findByCode(String code);

    List<Sector> findAll();

    boolean existsByCode(String code);
}
