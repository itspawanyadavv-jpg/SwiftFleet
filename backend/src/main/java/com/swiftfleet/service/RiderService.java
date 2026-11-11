package com.swiftfleet.service;

import com.swiftfleet.config.AppProperties;
import com.swiftfleet.model.DeliveryOrder;
import com.swiftfleet.model.GpsTelemetryEvent;
import com.swiftfleet.model.OrderStatus;
import com.swiftfleet.model.Rider;
import com.swiftfleet.model.RiderStatus;
import com.swiftfleet.store.FleetGeoLockStore;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Manages rider presence (ONLINE / OFFLINE) and real-time GPS updates
 * backed by Redis GEOADD / ZREM and STOMP broadcasts.
 */
@Service
public class RiderService {

    private final FleetGeoLockStore store;
    private final AppProperties properties;
    private final InspectorService inspectorService;
    private final SimpMessagingTemplate messagingTemplate;

    public RiderService(
            FleetGeoLockStore store,
            AppProperties properties,
            InspectorService inspectorService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.store = store;
        this.properties = properties;
        this.inspectorService = inspectorService;
        this.messagingTemplate = messagingTemplate;
    }

    public List<Rider> listRiders() {
        return store.findAllRiders();
    }

    public Rider getRider(String riderId) {
        return store.findRider(riderId)
                .orElseThrow(() -> new FleetException(HttpStatus.NOT_FOUND, "RIDER_NOT_FOUND", "Rider not found: " + riderId));
    }

    public Rider registerOrResetRider(Rider rider) {
        store.saveRider(rider);
        if (rider.status() != RiderStatus.OFFLINE) {
            store.upsertRiderGeo(rider.id(), rider.lat(), rider.lng());
        } else {
            store.removeRiderGeo(rider.id());
        }
        broadcastFleet();
        return rider;
    }

    public Rider setOnlineStatus(String riderId, boolean online) {
        Rider existing = getRider(riderId);
        if (!online && existing.activeOrderId() != null) {
            throw new FleetException(
                    HttpStatus.CONFLICT,
                    "RIDER_HAS_ACTIVE_ORDER",
                    "Cannot go offline while delivering order " + existing.activeOrderId()
            );
        }

        RiderStatus nextStatus = online
                ? (existing.activeOrderId() != null ? RiderStatus.BUSY : RiderStatus.IDLE)
                : RiderStatus.OFFLINE;
        Rider updated = existing.withStatusAndOrder(nextStatus, existing.activeOrderId(), Instant.now());
        store.saveRider(updated);

        long start = System.nanoTime();
        if (online) {
            store.upsertRiderGeo(updated.id(), updated.lat(), updated.lng());
            long ms = (System.nanoTime() - start) / 1_000_000;
            inspectorService.record(
                    "REDIS_GEO",
                    String.format(Locale.US, "GEOADD riders:geo %.6f %.6f %s", updated.lng(), updated.lat(), updated.id()),
                    updated.name() + " is now ONLINE (" + updated.status() + ")",
                    ms,
                    store.backendMode()
            );
        } else {
            store.removeRiderGeo(updated.id());
            long ms = (System.nanoTime() - start) / 1_000_000;
            inspectorService.record(
                    "REDIS_GEO",
                    "ZREM riders:geo " + updated.id(),
                    updated.name() + " went OFFLINE",
                    ms,
                    store.backendMode()
            );
        }

        broadcastFleet();
        return updated;
    }

    public GpsTelemetryEvent updateLocation(String riderId, double lat, double lng, Double speedKmh, Double bearingDeg, boolean logToInspector) {
        Rider existing = getRider(riderId);
        double resolvedSpeed = speedKmh != null ? speedKmh : 28.0;
        double resolvedBearing = bearingDeg != null
                ? bearingDeg
                : (Math.abs(existing.lat() - lat) < 1e-7 && Math.abs(existing.lng() - lng) < 1e-7
                ? existing.bearingDeg()
                : GeoMath.bearingDegrees(existing.lat(), existing.lng(), lat, lng));

        Rider updated = existing.withLocation(lat, lng, resolvedSpeed, resolvedBearing, Instant.now());
        if (updated.status() == RiderStatus.OFFLINE) {
            updated = updated.withStatusAndOrder(RiderStatus.IDLE, null, Instant.now());
        }
        store.saveRider(updated);

        long geoStart = System.nanoTime();
        store.upsertRiderGeo(updated.id(), lat, lng);
        long geoMs = (System.nanoTime() - geoStart) / 1_000_000;

        GpsTelemetryEvent telemetry = null;
        if (updated.activeOrderId() != null) {
            DeliveryOrder order = store.findOrder(updated.activeOrderId()).orElse(null);
            if (order != null && order.status() != OrderStatus.DELIVERED) {
                double remainingKm = calculateRemainingKm(order, lat, lng);
                int etaMinutes = GeoMath.estimateEtaMinutes(remainingKm, resolvedSpeed);
                double distToDropMeters = GeoMath.haversineMeters(lat, lng, order.dropLat(), order.dropLng());
                boolean insideGeofence = distToDropMeters <= properties.deliveryGeofenceMeters();

                DeliveryOrder updatedOrder = new DeliveryOrder(
                        order.id(),
                        order.customerName(),
                        order.customerAddress(),
                        order.itemsSummary(),
                        order.storeLat(),
                        order.storeLng(),
                        order.dropLat(),
                        order.dropLng(),
                        order.status(),
                        order.assignedRiderId(),
                        order.assignedRiderName(),
                        order.deliveryOtp(),
                        order.otpAttempts(),
                        order.otpLocked(),
                        GeoMath.round(remainingKm, 2),
                        etaMinutes,
                        order.routeSource(),
                        order.routePolyline(),
                        order.candidateRiders(),
                        order.createdAt(),
                        order.assignedAt(),
                        order.pickedUpAt(),
                        order.deliveredAt()
                );
                store.saveOrder(updatedOrder);

                telemetry = new GpsTelemetryEvent(
                        updated.id(),
                        order.id(),
                        lat,
                        lng,
                        GeoMath.round(resolvedSpeed, 1),
                        GeoMath.round(resolvedBearing, 1),
                        GeoMath.round(remainingKm, 2),
                        etaMinutes,
                        order.status(),
                        insideGeofence,
                        GeoMath.round(distToDropMeters, 1),
                        Instant.now()
                );
                messagingTemplate.convertAndSend("/topic/order/" + order.id() + "/telemetry", telemetry);
                messagingTemplate.convertAndSend("/topic/order/" + order.id(), updatedOrder);
            }
        }

        if (logToInspector) {
            inspectorService.record(
                    "STOMP_GPS",
                    String.format(Locale.US, "GEOADD riders:geo %.6f %.6f %s", lng, lat, riderId),
                    telemetry != null
                            ? String.format(Locale.US, "Order %s · %.2f km left · ETA %d min", telemetry.orderId(), telemetry.remainingKm(), telemetry.etaMinutes())
                            : updated.name() + " position updated",
                    geoMs,
                    store.backendMode()
            );
        }

        broadcastFleet();
        return telemetry;
    }

    public void broadcastFleet() {
        messagingTemplate.convertAndSend("/topic/fleet", store.findAllRiders());
    }

    private double calculateRemainingKm(DeliveryOrder order, double riderLat, double riderLng) {
        if (order.status() == OrderStatus.ASSIGNED) {
            double toStore = GeoMath.haversineKm(riderLat, riderLng, order.storeLat(), order.storeLng());
            double storeToDrop = GeoMath.haversineKm(order.storeLat(), order.storeLng(), order.dropLat(), order.dropLng());
            return toStore + storeToDrop;
        }
        return GeoMath.haversineKm(riderLat, riderLng, order.dropLat(), order.dropLng());
    }
}

