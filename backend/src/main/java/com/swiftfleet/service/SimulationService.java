package com.swiftfleet.service;

import com.swiftfleet.model.DeliveryOrder;
import com.swiftfleet.model.LatLng;
import com.swiftfleet.model.OrderStatus;
import com.swiftfleet.model.Rider;
import com.swiftfleet.store.FleetGeoLockStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives simulated road-polyline GPS telemetry over STOMP WebSockets for
 * end-to-end integration testing, live driver simulation, and route latency profiling.
 */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    private final FleetGeoLockStore store;
    private final RouteService routeService;
    private final RiderService riderService;
    private final DispatchService dispatchService;
    private final InspectorService inspectorService;

    // Bounded thread pool with CallerRunsPolicy to prevent OOM thread exhaustion under high simulation load
    private final ExecutorService executor = new ThreadPoolExecutor(
            4,
            16,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r, "simulation-worker-" + THREAD_COUNTER.incrementAndGet());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );
    private final ConcurrentHashMap<String, AtomicBoolean> activeTokens = new ConcurrentHashMap<>();

    public SimulationService(
            FleetGeoLockStore store,
            RouteService routeService,
            RiderService riderService,
            DispatchService dispatchService,
            InspectorService inspectorService
    ) {
        this.store = store;
        this.routeService = routeService;
        this.riderService = riderService;
        this.dispatchService = dispatchService;
        this.inspectorService = inspectorService;
    }

    public boolean isSimulating(String orderId) {
        AtomicBoolean flag = activeTokens.get(orderId);
        return flag != null && flag.get();
    }

    public void startSimulation(String orderId, boolean autoCompleteOtp) {
        DeliveryOrder order = dispatchService.getOrder(orderId);
        if (order.assignedRiderId() == null) {
            throw new FleetException(HttpStatus.CONFLICT, "ORDER_NOT_ASSIGNED", "Accept the order before simulating ride.");
        }
        if (order.status() == OrderStatus.DELIVERED) {
            return;
        }

        stopSimulation(orderId);
        AtomicBoolean running = new AtomicBoolean(true);
        activeTokens.put(orderId, running);

        executor.submit(() -> runSimulationLoop(orderId, autoCompleteOtp, running));
    }

    public void stopSimulation(String orderId) {
        AtomicBoolean existing = activeTokens.remove(orderId);
        if (existing != null) {
            existing.set(false);
        }
    }

    public void stopAll() {
        activeTokens.values().forEach(flag -> flag.set(false));
        activeTokens.clear();
    }

    private void runSimulationLoop(String orderId, boolean autoCompleteOtp, AtomicBoolean running) {
        try {
            DeliveryOrder order = dispatchService.getOrder(orderId);
            String riderId = order.assignedRiderId();
            Rider rider = riderService.getRider(riderId);

            inspectorService.record(
                    "SIMULATOR",
                    "SIMULATE RIDE " + orderId,
                    "Streaming road-snapped STOMP telemetry (" + order.routeSource() + ")",
                    1L,
                    store.backendMode()
            );

            if (order.status() == OrderStatus.ASSIGNED) {
                RouteService.RoutePlan pickupLeg = routeService.planLeg(
                        new LatLng(rider.lat(), rider.lng()),
                        new LatLng(order.storeLat(), order.storeLng())
                );
                walkWaypoints(riderId, pickupLeg.points(), 550L, 28.0, running);
                if (!running.get()) {
                    return;
                }
                order = dispatchService.markPickedUp(orderId, riderId);
            }

            if (!running.get()) {
                return;
            }

            Rider currentRider = riderService.getRider(riderId);
            LatLng legStart = new LatLng(currentRider.lat(), currentRider.lng());
            RouteService.RoutePlan dropLeg = routeService.planLeg(
                    legStart,
                    new LatLng(order.dropLat(), order.dropLng())
            );
            walkWaypoints(riderId, dropLeg.points(), 650L, 32.0, running);

            if (!running.get()) {
                return;
            }

            if (autoCompleteOtp) {
                Thread.sleep(500L);
                DeliveryOrder latest = dispatchService.getOrder(orderId);
                dispatchService.completeDelivery(orderId, riderId, latest.deliveryOtp());
            } else {
                inspectorService.record(
                        "GEOFENCE_GUARD",
                        "ARRIVED AT DROP-OFF (" + orderId + ")",
                        "Inside 150m geofence · awaiting 4-digit OTP (" + order.deliveryOtp() + ")",
                        1L,
                        store.backendMode()
                );
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("Simulation aborted for order {}: {}", orderId, ex.getMessage());
        } finally {
            activeTokens.remove(orderId, running);
        }
    }

    private void walkWaypoints(String riderId, List<LatLng> points, long delayMs, double baseSpeedKmh, AtomicBoolean running)
            throws InterruptedException {
        for (int i = 0; i < points.size(); i++) {
            if (!running.get()) {
                return;
            }
            LatLng pt = points.get(i);
            Double bearing = null;
            if (i + 1 < points.size()) {
                LatLng next = points.get(i + 1);
                bearing = GeoMath.bearingDegrees(pt.lat(), pt.lng(), next.lat(), next.lng());
            }
            double speed = (i == points.size() - 1) ? 0.0 : baseSpeedKmh + ((i % 3) * 2.5);
            riderService.updateLocation(riderId, pt.lat(), pt.lng(), speed, bearing, i % 3 == 0);
            if (i < points.size() - 1) {
                Thread.sleep(delayMs);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Gracefully shutting down SimulationService thread pool");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

