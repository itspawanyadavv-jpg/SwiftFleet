package com.swiftfleet.service;

import com.swiftfleet.config.AppProperties;
import com.swiftfleet.model.Rider;
import com.swiftfleet.model.RiderStatus;
import com.swiftfleet.store.FleetGeoLockStore;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Seeds realistic hyperlocal delivery riders around the store hub on startup
 * and exposes one-click fleet reset for live demos.
 */
@Service
public class DemoBootstrapService {

    private final FleetGeoLockStore store;
    private final AppProperties properties;
    private final RiderService riderService;
    private final SimulationService simulationService;
    private final InspectorService inspectorService;
    private final DispatchService dispatchService;

    public DemoBootstrapService(
            FleetGeoLockStore store,
            AppProperties properties,
            RiderService riderService,
            SimulationService simulationService,
            InspectorService inspectorService,
            DispatchService dispatchService
    ) {
        this.store = store;
        this.properties = properties;
        this.riderService = riderService;
        this.simulationService = simulationService;
        this.inspectorService = inspectorService;
        this.dispatchService = dispatchService;
    }

    @PostConstruct
    public void init() {
        if (store.findAllRiders().isEmpty()) {
            seedRiders();
        }
    }

    public void resetDemo() {
        simulationService.stopAll();
        store.clearAll();
        inspectorService.clear();
        seedRiders();
        dispatchService.broadcastOrderListReset();
    }

    public void seedRiders() {
        double lat = properties.storeLat();
        double lng = properties.storeLng();
        Instant now = Instant.now();

        List<Rider> initialRiders = List.of(
                new Rider("rdr-1", "Aarav Sharma", "DL-01-SF-2041", "+91 98110-40101",
                        RiderStatus.IDLE, lat + 0.0032, lng - 0.0041, 0.0, 45.0, null, now),
                new Rider("rdr-2", "Rohan Verma", "UP-14-SF-8819", "+91 98110-40202",
                        RiderStatus.IDLE, lat - 0.0048, lng + 0.0038, 0.0, 315.0, null, now),
                new Rider("rdr-3", "Vikram Singh", "UP-14-SF-5520", "+91 98110-40303",
                        RiderStatus.IDLE, lat + 0.0110, lng + 0.0095, 0.0, 210.0, null, now),
                new Rider("rdr-4", "Kabir Mehta", "DL-08-SF-9934", "+91 98110-40404",
                        RiderStatus.OFFLINE, lat - 0.0160, lng - 0.0140, 0.0, 90.0, null, now)
        );

        for (Rider rider : initialRiders) {
            riderService.registerOrResetRider(rider);
        }

        inspectorService.record(
                "BOOTSTRAP",
                "SEED 4 DEMO RIDERS -> GEOADD riders:geo",
                "3 IDLE riders online within 3 km · 1 OFFLINE rider",
                1L,
                store.backendMode()
        );
    }
}

