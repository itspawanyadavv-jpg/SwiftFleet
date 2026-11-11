package com.swiftfleet.service;

import com.swiftfleet.config.AppProperties;
import com.swiftfleet.model.DeliveryOrder;
import com.swiftfleet.model.DispatchAlertEvent;
import com.swiftfleet.model.LatLng;
import com.swiftfleet.model.NearbyRiderMatch;
import com.swiftfleet.model.OrderStatus;
import com.swiftfleet.model.RaceConditionReport;
import com.swiftfleet.model.Rider;
import com.swiftfleet.model.RiderStatus;
import com.swiftfleet.model.StoreConfig;
import com.swiftfleet.store.FleetGeoLockStore;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core order dispatch engine:
 * 1. Geospatial matching (Redis GEOSEARCH within store radius)
 * 2. Concurrency-safe order claim (atomic SET NX EX distributed lock + Lua rollback)
 * 3. Geofenced 4-digit OTP delivery handshake
 */
@Service
public class DispatchService {

    private final FleetGeoLockStore store;
    private final AppProperties properties;
    private final StoreConfigService storeConfigService;
    private final RouteService routeService;
    private final RiderService riderService;
    private final InspectorService inspectorService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicInteger orderSeq = new AtomicInteger(100);

    public DispatchService(
            FleetGeoLockStore store,
            AppProperties properties,
            StoreConfigService storeConfigService,
            RouteService routeService,
            RiderService riderService,
            InspectorService inspectorService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.store = store;
        this.properties = properties;
        this.storeConfigService = storeConfigService;
        this.routeService = routeService;
        this.riderService = riderService;
        this.inspectorService = inspectorService;
        this.messagingTemplate = messagingTemplate;
    }

    public List<DeliveryOrder> listOrders() {
        return store.findAllOrders();
    }

    public DeliveryOrder getOrder(String orderId) {
        return store.findOrder(orderId)
                .orElseThrow(() -> new FleetException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found: " + orderId));
    }

    public List<NearbyRiderMatch> findNearbyRiders(double lat, double lng, Double radiusOverrideKm) {
        StoreConfig cfg = storeConfigService.currentConfig();
        double radiusKm = radiusOverrideKm != null && radiusOverrideKm > 0 ? radiusOverrideKm : cfg.dispatchRadiusKm();

        long start = System.nanoTime();
        List<FleetGeoLockStore.GeoCandidate> candidates = store.findNearbyRiderIds(lat, lng, radiusKm);
        long ms = (System.nanoTime() - start) / 1_000_000;

        List<NearbyRiderMatch> matches = new ArrayList<>();
        for (FleetGeoLockStore.GeoCandidate candidate : candidates) {
            store.findRider(candidate.riderId()).ifPresent(rider -> matches.add(
                    new NearbyRiderMatch(
                            rider.id(),
                            rider.name(),
                            rider.vehicleNumber(),
                            rider.status(),
                            GeoMath.round(candidate.distanceKm(), 2),
                            candidate.lat(),
                            candidate.lng()
                    )
            ));
        }

        inspectorService.record(
                "REDIS_GEO",
                String.format(Locale.US, "GEOSEARCH riders:geo FROMLONLAT %.6f %.6f BYRADIUS %.1f km ASC", lng, lat, radiusKm),
                "Found " + matches.size() + " online rider(s) within " + radiusKm + " km",
                ms,
                store.backendMode()
        );
        return matches;
    }

    public DeliveryOrder createOrder(String customerName, String customerAddress, String itemsSummary, double dropLat, double dropLng) {
        StoreConfig cfg = storeConfigService.currentConfig();
        String orderId = "ORD-" + orderSeq.incrementAndGet();
        String otp = String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000));

        List<NearbyRiderMatch> nearby = findNearbyRiders(cfg.storeLat(), cfg.storeLng(), cfg.dispatchRadiusKm());
        List<NearbyRiderMatch> idleCandidates = nearby.stream()
                .filter(m -> m.status() == RiderStatus.IDLE)
                .toList();

        RouteService.RoutePlan previewPlan = routeService.planLeg(
                new LatLng(cfg.storeLat(), cfg.storeLng()),
                new LatLng(dropLat, dropLng)
        );

        DeliveryOrder order = new DeliveryOrder(
                orderId,
                customerName == null || customerName.isBlank() ? "Aarushi Verma" : customerName.trim(),
                customerAddress == null || customerAddress.isBlank() ? "Shipra Suncity, Indirapuram" : customerAddress.trim(),
                itemsSummary == null || itemsSummary.isBlank() ? "Fresh Milk 1L, Brown Bread, Eggs (6 pcs)" : itemsSummary.trim(),
                cfg.storeLat(),
                cfg.storeLng(),
                dropLat,
                dropLng,
                OrderStatus.PENDING_DISPATCH,
                null,
                null,
                otp,
                0,
                false,
                previewPlan.totalKm(),
                GeoMath.estimateEtaMinutes(previewPlan.totalKm(), 26.0),
                previewPlan.source(),
                previewPlan.points(),
                idleCandidates,
                Instant.now(),
                null,
                null,
                null
        );
        store.saveOrder(order);

        for (NearbyRiderMatch match : idleCandidates) {
            DispatchAlertEvent alert = new DispatchAlertEvent(
                    orderId,
                    match.riderId(),
                    order.customerName(),
                    order.customerAddress(),
                    order.itemsSummary(),
                    order.storeLat(),
                    order.storeLng(),
                    order.dropLat(),
                    order.dropLng(),
                    match.distanceKm(),
                    Instant.now()
            );
            messagingTemplate.convertAndSend("/topic/dispatch/" + match.riderId(), alert);
        }

        inspectorService.record(
                "STOMP_DISPATCH",
                "SEND /topic/dispatch/{riderId}",
                "Order " + orderId + " pinged " + idleCandidates.size() + " idle rider(s) (OTP=" + otp + ")",
                1L,
                store.backendMode()
        );

        broadcastOrder(order);
        return order;
    }

    public DeliveryOrder acceptOrder(String orderId, String riderId) {
        Rider rider = riderService.getRider(riderId);
        if (rider.status() == RiderStatus.OFFLINE) {
            throw new FleetException(HttpStatus.CONFLICT, "RIDER_OFFLINE", "Rider must be online to accept orders.");
        }
        if (rider.status() == RiderStatus.BUSY && !orderId.equals(rider.activeOrderId())) {
            throw new FleetException(HttpStatus.CONFLICT, "RIDER_BUSY", rider.name() + " is already delivering " + rider.activeOrderId());
        }

        long lockStart = System.nanoTime();
        boolean acquired = store.tryAcquireOrderLock(orderId, riderId, properties.orderLockTtlSeconds());
        long lockMs = (System.nanoTime() - lockStart) / 1_000_000;

        if (!acquired) {
            inspectorService.record(
                    "REDIS_LOCK",
                    String.format("SET lock:order:%s %s NX EX %d -> (nil)", orderId, riderId, properties.orderLockTtlSeconds()),
                    "409 Conflict: " + rider.name() + " blocked by distributed lock",
                    lockMs,
                    store.backendMode()
            );
            throw new FleetException(
                    HttpStatus.CONFLICT,
                    "ORDER_ALREADY_CLAIMED",
                    "Order " + orderId + " was already claimed by another rider."
            );
        }

        try {
            DeliveryOrder existing = getOrder(orderId);
            if (existing.status() != OrderStatus.PENDING_DISPATCH) {
                throw new FleetException(
                        HttpStatus.CONFLICT,
                        "ORDER_ALREADY_ASSIGNED",
                        "Order " + orderId + " is already " + existing.status()
                );
            }

            RouteService.RoutePlan pickupLeg = routeService.planLeg(
                    new LatLng(rider.lat(), rider.lng()),
                    new LatLng(existing.storeLat(), existing.storeLng())
            );
            RouteService.RoutePlan dropLeg = routeService.planLeg(
                    new LatLng(existing.storeLat(), existing.storeLng()),
                    new LatLng(existing.dropLat(), existing.dropLng())
            );

            List<LatLng> fullRoute = new ArrayList<>(pickupLeg.points());
            if (!dropLeg.points().isEmpty()) {
                fullRoute.addAll(dropLeg.points().subList(1, dropLeg.points().size()));
            }
            double totalKm = GeoMath.round(pickupLeg.totalKm() + dropLeg.totalKm(), 2);

            DeliveryOrder assigned = new DeliveryOrder(
                    existing.id(),
                    existing.customerName(),
                    existing.customerAddress(),
                    existing.itemsSummary(),
                    existing.storeLat(),
                    existing.storeLng(),
                    existing.dropLat(),
                    existing.dropLng(),
                    OrderStatus.ASSIGNED,
                    rider.id(),
                    rider.name(),
                    existing.deliveryOtp(),
                    existing.otpAttempts(),
                    existing.otpLocked(),
                    totalKm,
                    GeoMath.estimateEtaMinutes(totalKm, 28.0),
                    dropLeg.source(),
                    fullRoute,
                    existing.candidateRiders(),
                    existing.createdAt(),
                    Instant.now(),
                    null,
                    null
            );

            store.saveOrder(assigned);
            store.saveRider(rider.withStatusAndOrder(RiderStatus.BUSY, orderId, Instant.now()));

            inspectorService.record(
                    "REDIS_LOCK",
                    String.format("SET lock:order:%s %s NX EX %d -> OK", orderId, riderId, properties.orderLockTtlSeconds()),
                    rider.name() + " won lock & claimed " + orderId,
                    lockMs,
                    store.backendMode()
            );

            broadcastOrder(assigned);
            riderService.broadcastFleet();
            return assigned;
        } catch (RuntimeException ex) {
            store.releaseOrderLock(orderId, riderId);
            throw ex;
        }
    }

    public DeliveryOrder markPickedUp(String orderId, String riderId) {
        DeliveryOrder existing = getOrder(orderId);
        if (!riderId.equals(existing.assignedRiderId())) {
            throw new FleetException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_RIDER", "Only assigned rider can pick up this order.");
        }
        if (existing.status() == OrderStatus.OUT_FOR_DELIVERY || existing.status() == OrderStatus.PICKED_UP) {
            return existing;
        }
        if (existing.status() != OrderStatus.ASSIGNED) {
            throw new FleetException(HttpStatus.CONFLICT, "INVALID_ORDER_STATE", "Order cannot be picked up in state " + existing.status());
        }

        RouteService.RoutePlan dropLeg = routeService.planLeg(
                new LatLng(existing.storeLat(), existing.storeLng()),
                new LatLng(existing.dropLat(), existing.dropLng())
        );

        DeliveryOrder updated = new DeliveryOrder(
                existing.id(),
                existing.customerName(),
                existing.customerAddress(),
                existing.itemsSummary(),
                existing.storeLat(),
                existing.storeLng(),
                existing.dropLat(),
                existing.dropLng(),
                OrderStatus.OUT_FOR_DELIVERY,
                existing.assignedRiderId(),
                existing.assignedRiderName(),
                existing.deliveryOtp(),
                existing.otpAttempts(),
                existing.otpLocked(),
                dropLeg.totalKm(),
                GeoMath.estimateEtaMinutes(dropLeg.totalKm(), 28.0),
                dropLeg.source(),
                dropLeg.points(),
                existing.candidateRiders(),
                existing.createdAt(),
                existing.assignedAt(),
                Instant.now(),
                null
        );
        store.saveOrder(updated);

        inspectorService.record(
                "DISPATCH_STATE",
                "ORDER " + orderId + " -> OUT_FOR_DELIVERY",
                existing.assignedRiderName() + " picked up package · OTP=" + existing.deliveryOtp(),
                1L,
                store.backendMode()
        );
        broadcastOrder(updated);
        return updated;
    }

    public DeliveryOrder completeDelivery(String orderId, String riderId, String otp) {
        DeliveryOrder existing = getOrder(orderId);
        if (!riderId.equals(existing.assignedRiderId())) {
            throw new FleetException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_RIDER", "Only assigned rider can complete this order.");
        }
        if (existing.status() == OrderStatus.DELIVERED) {
            return existing;
        }
        if (existing.status() != OrderStatus.OUT_FOR_DELIVERY && existing.status() != OrderStatus.PICKED_UP) {
            throw new FleetException(HttpStatus.CONFLICT, "ORDER_NOT_OUT_FOR_DELIVERY", "Pick up the order before completing delivery.");
        }

        Rider rider = riderService.getRider(riderId);
        double distanceMeters = GeoMath.haversineMeters(rider.lat(), rider.lng(), existing.dropLat(), existing.dropLng());
        if (distanceMeters > properties.deliveryGeofenceMeters()) {
            inspectorService.record(
                    "GEOFENCE_GUARD",
                    String.format(Locale.US, "GEOFENCE CHECK %.0fm > %.0fm", distanceMeters, properties.deliveryGeofenceMeters()),
                    "Blocked premature completion for " + orderId,
                    1L,
                    store.backendMode()
            );
            throw new FleetException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "OUTSIDE_DELIVERY_GEOFENCE",
                    String.format(Locale.US, "Too far from drop-off (%.0f m away; must be within %.0f m).",
                            distanceMeters, properties.deliveryGeofenceMeters())
            );
        }

        if (existing.otpLocked() || existing.otpAttempts() >= properties.otpMaxAttempts()) {
            throw new FleetException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "OTP_ATTEMPTS_EXCEEDED",
                    "Too many invalid OTP attempts. Reset demo or create a new order."
            );
        }

        String sanitizedOtp = otp == null ? "" : otp.trim();
        if (!existing.deliveryOtp().equals(sanitizedOtp)) {
            int nextAttempts = existing.otpAttempts() + 1;
            boolean locked = nextAttempts >= properties.otpMaxAttempts();
            DeliveryOrder failedUpdate = new DeliveryOrder(
                    existing.id(), existing.customerName(), existing.customerAddress(), existing.itemsSummary(),
                    existing.storeLat(), existing.storeLng(), existing.dropLat(), existing.dropLng(),
                    existing.status(), existing.assignedRiderId(), existing.assignedRiderName(),
                    existing.deliveryOtp(), nextAttempts, locked, existing.remainingKm(), existing.etaMinutes(),
                    existing.routeSource(), existing.routePolyline(), existing.candidateRiders(),
                    existing.createdAt(), existing.assignedAt(), existing.pickedUpAt(), existing.deliveredAt()
            );
            store.saveOrder(failedUpdate);
            broadcastOrder(failedUpdate);

            int remaining = Math.max(0, properties.otpMaxAttempts() - nextAttempts);
            inspectorService.record(
                    "OTP_HANDSHAKE",
                    "VERIFY OTP " + orderId + " -> MISMATCH",
                    "Invalid OTP entered (" + remaining + " attempt(s) left)",
                    1L,
                    store.backendMode()
            );
            throw new FleetException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_OTP",
                    "Incorrect 4-digit OTP. " + remaining + " attempt(s) remaining."
            );
        }

        DeliveryOrder delivered = new DeliveryOrder(
                existing.id(),
                existing.customerName(),
                existing.customerAddress(),
                existing.itemsSummary(),
                existing.storeLat(),
                existing.storeLng(),
                existing.dropLat(),
                existing.dropLng(),
                OrderStatus.DELIVERED,
                existing.assignedRiderId(),
                existing.assignedRiderName(),
                existing.deliveryOtp(),
                existing.otpAttempts(),
                false,
                0.0,
                0,
                existing.routeSource(),
                existing.routePolyline(),
                existing.candidateRiders(),
                existing.createdAt(),
                existing.assignedAt(),
                existing.pickedUpAt(),
                Instant.now()
        );
        store.saveOrder(delivered);
        store.saveRider(rider.withStatusAndOrder(RiderStatus.IDLE, null, Instant.now()));
        store.releaseOrderLock(orderId, riderId);

        inspectorService.record(
                "OTP_HANDSHAKE",
                "VERIFY OTP " + orderId + " -> DELIVERED + EVAL unlock.lua",
                String.format(Locale.US, "Verified within %.0f m geofence · %s now IDLE", distanceMeters, rider.name()),
                2L,
                store.backendMode()
        );

        broadcastOrder(delivered);
        riderService.broadcastFleet();
        return delivered;
    }

    /**
     * Fires two threads simultaneously through a CountDownLatch barrier to prove
     * that distributed locking eliminates double-assignment race conditions.
     */
    public RaceConditionReport simulateRaceCondition(String existingOrderId) {
        List<Rider> idleRiders = store.findAllRiders().stream()
                .filter(r -> r.status() == RiderStatus.IDLE)
                .toList();

        if (idleRiders.size() < 2) {
            List<Rider> candidates = store.findAllRiders().stream()
                    .filter(r -> r.status() == RiderStatus.OFFLINE)
                    .limit(2 - idleRiders.size())
                    .toList();
            for (Rider off : candidates) {
                riderService.setOnlineStatus(off.id(), true);
            }
            idleRiders = store.findAllRiders().stream()
                    .filter(r -> r.status() == RiderStatus.IDLE)
                    .toList();
        }

        if (idleRiders.size() < 2) {
            throw new FleetException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_IDLE_RIDERS",
                    "Need at least 2 IDLE riders to simulate a dispatch race condition. Reset fleet or free a rider."
            );
        }

        DeliveryOrder targetOrder;
        if (existingOrderId != null && !existingOrderId.isBlank()) {
            DeliveryOrder found = getOrder(existingOrderId);
            targetOrder = found.status() == OrderStatus.PENDING_DISPATCH
                    ? found
                    : createOrder("Race Test Customer", "Vaishali Sector 4, Ghaziabad", "Race Condition Chaos Test",
                            properties.storeLat() + 0.0085, properties.storeLng() + 0.0075);
        } else {
            targetOrder = createOrder("Race Test Customer", "Vaishali Sector 4, Ghaziabad", "Race Condition Chaos Test",
                    properties.storeLat() + 0.0085, properties.storeLng() + 0.0075);
        }

        Rider riderA = idleRiders.get(0);
        Rider riderB = idleRiders.get(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        long startNs = System.nanoTime();

        try {
            Callable<RaceConditionReport.AttemptResult> taskA = () -> runConcurrentClaim(targetOrder.id(), riderA, ready, fire);
            Callable<RaceConditionReport.AttemptResult> taskB = () -> runConcurrentClaim(targetOrder.id(), riderB, ready, fire);

            Future<RaceConditionReport.AttemptResult> futureA = pool.submit(taskA);
            Future<RaceConditionReport.AttemptResult> futureB = pool.submit(taskB);

            ready.await(2, TimeUnit.SECONDS);
            fire.countDown();

            RaceConditionReport.AttemptResult resA = futureA.get(5, TimeUnit.SECONDS);
            RaceConditionReport.AttemptResult resB = futureB.get(5, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

            RaceConditionReport.AttemptResult winner = resA.won() ? resA : (resB.won() ? resB : resA);
            return new RaceConditionReport(
                    targetOrder.id(),
                    winner.won() ? winner.riderId() : null,
                    winner.won() ? winner.riderName() : null,
                    store.backendMode().equals("REDIS_LIVE")
                            ? "Redis Atomic SET NX EX + Lua Guard"
                            : "In-Memory Atomic CAS Lock (NX TTL)",
                    elapsedMs,
                    List.of(resA, resB)
            );
        } catch (Exception ex) {
            throw new FleetException(HttpStatus.INTERNAL_SERVER_ERROR, "RACE_SIM_ERROR", "Race simulation failed: " + ex.getMessage());
        } finally {
            pool.shutdownNow();
        }
    }

    private RaceConditionReport.AttemptResult runConcurrentClaim(
            String orderId,
            Rider rider,
            CountDownLatch ready,
            CountDownLatch fire
    ) {
        ready.countDown();
        try {
            fire.await(2, TimeUnit.SECONDS);
            acceptOrder(orderId, rider.id());
            return new RaceConditionReport.AttemptResult(
                    rider.id(), rider.name(), 200, true, "Lock acquired — Order assigned"
            );
        } catch (FleetException ex) {
            return new RaceConditionReport.AttemptResult(
                    rider.id(), rider.name(), ex.getStatus().value(), false, ex.getMessage()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new RaceConditionReport.AttemptResult(
                    rider.id(), rider.name(), 500, false, "Interrupted"
            );
        }
    }

    public void broadcastOrder(DeliveryOrder order) {
        messagingTemplate.convertAndSend("/topic/order/" + order.id(), order);
        messagingTemplate.convertAndSend("/topic/orders", store.findAllOrders());
    }

    public void broadcastOrderListReset() {
        messagingTemplate.convertAndSend("/topic/orders", store.findAllOrders());
    }
}
