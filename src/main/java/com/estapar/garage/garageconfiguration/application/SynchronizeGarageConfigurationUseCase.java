package com.estapar.garage.garageconfiguration.application;

import com.estapar.garage.garageconfiguration.application.GarageCatalog.CatalogSector;
import com.estapar.garage.garageconfiguration.application.GarageCatalog.CatalogSpot;
import com.estapar.garage.garageconfiguration.domain.Sector;
import com.estapar.garage.garageconfiguration.domain.SectorRepository;
import com.estapar.garage.parking.domain.GarageStateRepository;
import com.estapar.garage.parking.domain.ParkingSessionRepository;
import com.estapar.garage.parking.domain.ParkingSpot;
import com.estapar.garage.parking.domain.ParkingSpotRepository;
import com.estapar.garage.parking.domain.SpotStatus;
import com.estapar.garage.shared.configuration.GarageProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fetches the catalog from the simulator (limited retries, exponential backoff — no retry
 * storms, no crash loop) and persists it idempotently. HTTP happens strictly outside the
 * database transaction; the transactional apply is delegated to {@link TransactionTemplate}
 * to keep the boundary explicit.
 */
@Service
public class SynchronizeGarageConfigurationUseCase {

    private static final Logger log = LoggerFactory.getLogger(SynchronizeGarageConfigurationUseCase.class);

    private final GarageCatalogClient catalogClient;
    private final SectorRepository sectorRepository;
    private final ParkingSpotRepository spotRepository;
    private final ParkingSessionRepository sessionRepository;
    private final GarageStateRepository garageStateRepository;
    private final TransactionTemplate transactionTemplate;
    private final GarageReadiness readiness;
    private final GarageProperties properties;
    private final Clock clock;

    public SynchronizeGarageConfigurationUseCase(
            GarageCatalogClient catalogClient,
            SectorRepository sectorRepository,
            ParkingSpotRepository spotRepository,
            ParkingSessionRepository sessionRepository,
            GarageStateRepository garageStateRepository,
            TransactionTemplate transactionTemplate,
            GarageReadiness readiness,
            GarageProperties properties,
            Clock clock) {
        this.catalogClient = catalogClient;
        this.sectorRepository = sectorRepository;
        this.spotRepository = spotRepository;
        this.sessionRepository = sessionRepository;
        this.garageStateRepository = garageStateRepository;
        this.transactionTemplate = transactionTemplate;
        this.readiness = readiness;
        this.properties = properties;
        this.clock = clock;
    }

    public SyncResult execute() {
        GarageCatalog catalog = fetchWithRetry();
        SyncResult result = transactionTemplate.execute(status -> apply(catalog));
        readiness.markReady();
        log.info(
                "garage_configuration_synchronized sectors={} spots={} totalCapacity={}",
                result.sectors(),
                result.spots(),
                result.totalCapacity());
        return result;
    }

    /**
     * Fallback for a failed sync on a warm database: a previous run's configuration is still
     * valid reference data, so the service can keep processing events instead of going dark.
     */
    public boolean recoverFromExistingConfiguration() {
        if (sectorRepository.findAll().isEmpty()) {
            return false;
        }
        readiness.markReady();
        log.warn("garage_configuration_sync_failed recovered=true detail=using previously synchronized data");
        return true;
    }

    private GarageCatalog fetchWithRetry() {
        var sync = properties.simulator().sync();
        Duration backoff = sync.initialBackoff();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= sync.maxAttempts(); attempt++) {
            try {
                return catalogClient.fetch();
            } catch (GarageCatalogFetchException e) {
                lastFailure = e;
                log.warn(
                        "garage_configuration_sync_failed attempt={}/{} backoffMs={} reason={}",
                        attempt,
                        sync.maxAttempts(),
                        backoff.toMillis(),
                        e.getMessage());
                if (attempt < sync.maxAttempts() && !sleep(backoff)) {
                    break;
                }
                backoff = nextBackoff(backoff, sync.maxBackoff());
            }
        }
        throw new GarageCatalogFetchException(
                "Could not fetch garage configuration after " + sync.maxAttempts() + " attempts", lastFailure);
    }

    private SyncResult apply(GarageCatalog catalog) {
        catalog.sectors().forEach(this::upsertSector);
        catalog.spots().forEach(this::upsertSpot);
        warnOnCapacityMismatch(catalog);

        var garage = garageStateRepository.lock();
        long activeSessions = sessionRepository.countActive();
        garage.synchronize(catalog.totalCapacity(), Math.toIntExact(activeSessions), clock.instant());
        garageStateRepository.save(garage);

        return new SyncResult(
                catalog.sectors().size(), catalog.spots().size(), catalog.totalCapacity(), activeSessions);
    }

    private void upsertSector(CatalogSector sector) {
        sectorRepository.save(new Sector(sector.code(), sector.basePrice(), sector.maxCapacity()));
    }

    /** New spots are created AVAILABLE; existing spots keep their occupation state untouched. */
    private void upsertSpot(CatalogSpot spot) {
        var existing = spotRepository.findByExternalId(spot.externalId());
        if (existing.isEmpty()) {
            spotRepository.save(new ParkingSpot(
                    null, spot.externalId(), spot.sectorCode(), spot.coordinates(), SpotStatus.AVAILABLE, null));
            return;
        }
        var current = existing.get();
        if (!current.isAvailable()) {
            return; // never overwrite an occupied spot during re-sync (spec 02 R6)
        }
        spotRepository.save(new ParkingSpot(
                current.id(), spot.externalId(), spot.sectorCode(), spot.coordinates(), current.status(), null));
    }

    private void warnOnCapacityMismatch(GarageCatalog catalog) {
        Map<String, Long> spotsPerSector =
                catalog.spots().stream().collect(Collectors.groupingBy(CatalogSpot::sectorCode, Collectors.counting()));
        for (CatalogSector sector : catalog.sectors()) {
            long declared = sector.maxCapacity();
            long actual = spotsPerSector.getOrDefault(sector.code(), 0L);
            if (declared != actual) {
                log.warn(
                        "garage_configuration_inconsistency sector={} declaredCapacity={} spotCount={}",
                        sector.code(),
                        declared,
                        actual);
            }
        }
    }

    private boolean sleep(Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Duration nextBackoff(Duration current, Duration max) {
        Duration doubled = current.multipliedBy(2);
        return doubled.compareTo(max) > 0 ? max : doubled;
    }

    public record SyncResult(int sectors, int spots, int totalCapacity, long activeSessionsReconciled) {}
}
