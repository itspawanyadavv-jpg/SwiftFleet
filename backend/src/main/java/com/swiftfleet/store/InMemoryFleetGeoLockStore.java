package com.swiftfleet.store;

import com.swiftfleet.model.DeliveryOrder;
import com.swiftfleet.model.LatLng;
import com.swiftfleet.model.Rider;
import com.swiftfleet.service.GeoMath;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe in-memory fallback used only when Redis is unavailable locally or
 * when FLEET_STORE_MODE=memory is selected (e.g. unit tests / offline demos).
 */
public class InMemoryFleetGeoLockStore implements FleetGeoLockStore {

    private record LockEntry(String riderId, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, LatLng> geoIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LockEntry> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Rider> riders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DeliveryOrder> orders = new ConcurrentHashMap<>();

    @Override
    public String backendMode() {
        return "IN_MEMORY_FALLBACK";
    }

    @Override
    public void upsertRiderGeo(String riderId, double lat, double lng) {
        geoIndex.put(riderId, new LatLng(lat, lng));
    }

    @Override
    public void removeRiderGeo(String riderId) {
        geoIndex.remove(riderId);
    }

    @Override
    public List<GeoCandidate> findNearbyRiderIds(double lat, double lng, double radiusKm) {
        return geoIndex.entrySet().stream()
                .map(entry -> {
                    LatLng pos = entry.getValue();
                    double dist = GeoMath.haversineKm(lat, lng, pos.lat(), pos.lng());
                    return new GeoCandidate(entry.getKey(), dist, pos.lat(), pos.lng());
                })
                .filter(candidate -> candidate.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(GeoCandidate::distanceKm))
                .toList();
    }

    @Override
    public boolean tryAcquireOrderLock(String orderId, String riderId, long ttlSeconds) {
        Instant now = Instant.now();
        AtomicBoolean acquired = new AtomicBoolean(false);
        locks.compute(orderId, (key, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(now)) {
                acquired.set(true);
                return new LockEntry(riderId, now.plusSeconds(ttlSeconds));
            }
            return existing;
        });
        return acquired.get();
    }

    @Override
    public boolean releaseOrderLock(String orderId, String riderId) {
        AtomicBoolean released = new AtomicBoolean(false);
        locks.computeIfPresent(orderId, (key, existing) -> {
            if (existing.riderId().equals(riderId)) {
                released.set(true);
                return null;
            }
            return existing;
        });
        return released.get();
    }

    @Override
    public void saveRider(Rider rider) {
        riders.put(rider.id(), rider);
    }

    @Override
    public Optional<Rider> findRider(String riderId) {
        return Optional.ofNullable(riders.get(riderId));
    }

    @Override
    public List<Rider> findAllRiders() {
        return riders.values().stream()
                .sorted(Comparator.comparing(Rider::id))
                .toList();
    }

    @Override
    public void saveOrder(DeliveryOrder order) {
        orders.put(order.id(), order);
    }

    @Override
    public Optional<DeliveryOrder> findOrder(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public List<DeliveryOrder> findAllOrders() {
        return orders.values().stream()
                .sorted(Comparator.comparing(DeliveryOrder::createdAt).reversed())
                .toList();
    }

    @Override
    public void clearAll() {
        geoIndex.clear();
        locks.clear();
        riders.clear();
        orders.clear();
    }
}

