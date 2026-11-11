package com.swiftfleet.store;

import com.swiftfleet.model.DeliveryOrder;
import com.swiftfleet.model.Rider;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction over geospatial indexing, atomic distributed locks, and fleet state.
 * Implemented by RedisFleetGeoLockStore (production/Docker Redis) and
 * InMemoryFleetGeoLockStore (local fallback when Redis is offline).
 */
public interface FleetGeoLockStore {

    record GeoCandidate(String riderId, double distanceKm, double lat, double lng) {
    }

    String backendMode();

    void upsertRiderGeo(String riderId, double lat, double lng);

    void removeRiderGeo(String riderId);

    List<GeoCandidate> findNearbyRiderIds(double lat, double lng, double radiusKm);

    boolean tryAcquireOrderLock(String orderId, String riderId, long ttlSeconds);

    boolean releaseOrderLock(String orderId, String riderId);

    void saveRider(Rider rider);

    Optional<Rider> findRider(String riderId);

    List<Rider> findAllRiders();

    void saveOrder(DeliveryOrder order);

    Optional<DeliveryOrder> findOrder(String orderId);

    List<DeliveryOrder> findAllOrders();

    void clearAll();
}

