package com.swiftfleet.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftfleet.model.DeliveryOrder;
import com.swiftfleet.model.Rider;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.domain.geo.GeoReference;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Production Redis implementation using GeoOperations (GEOADD / GEOSEARCH),
 * atomic SET NX EX distributed locks, and Lua-guarded lock release.
 */
public class RedisFleetGeoLockStore implements FleetGeoLockStore {

    public static final String RIDER_GEO_KEY = "riders:geo";
    private static final String RIDERS_HASH_KEY = "swiftfleet:riders";
    private static final String ORDERS_HASH_KEY = "swiftfleet:orders";
    private static final String LOCK_PREFIX = "lock:order:";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisFleetGeoLockStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String backendMode() {
        return "REDIS_LIVE";
    }

    @Override
    public void upsertRiderGeo(String riderId, double lat, double lng) {
        redisTemplate.opsForGeo().add(RIDER_GEO_KEY, new Point(lng, lat), riderId);
    }

    @Override
    public void removeRiderGeo(String riderId) {
        redisTemplate.opsForGeo().remove(RIDER_GEO_KEY, riderId);
    }

    @Override
    public List<GeoCandidate> findNearbyRiderIds(double lat, double lng, double radiusKm) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                RIDER_GEO_KEY,
                GeoReference.fromCoordinate(lng, lat),
                new Distance(radiusKm, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeCoordinates()
                        .includeDistance()
                        .sortAscending()
        );
        List<GeoCandidate> candidates = new ArrayList<>();
        if (results == null) {
            return candidates;
        }
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
            RedisGeoCommands.GeoLocation<String> content = result.getContent();
            Point point = content.getPoint();
            double distKm = result.getDistance() != null ? result.getDistance().getValue() : 0.0;
            double candLat = point != null ? point.getY() : lat;
            double candLng = point != null ? point.getX() : lng;
            candidates.add(new GeoCandidate(content.getName(), distKm, candLat, candLng));
        }
        return candidates;
    }

    @Override
    public boolean tryAcquireOrderLock(String orderId, String riderId, long ttlSeconds) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + orderId, riderId, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public boolean releaseOrderLock(String orderId, String riderId) {
        Long deleted = redisTemplate.execute(UNLOCK_SCRIPT, List.of(LOCK_PREFIX + orderId), riderId);
        return deleted != null && deleted > 0;
    }

    @Override
    public void saveRider(Rider rider) {
        redisTemplate.opsForHash().put(RIDERS_HASH_KEY, rider.id(), toJson(rider));
    }

    @Override
    public Optional<Rider> findRider(String riderId) {
        Object raw = redisTemplate.opsForHash().get(RIDERS_HASH_KEY, riderId);
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(fromJson(raw.toString(), Rider.class));
    }

    @Override
    public List<Rider> findAllRiders() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(RIDERS_HASH_KEY);
        return entries.values().stream()
                .map(val -> fromJson(val.toString(), Rider.class))
                .sorted(Comparator.comparing(Rider::id))
                .toList();
    }

    @Override
    public void saveOrder(DeliveryOrder order) {
        redisTemplate.opsForHash().put(ORDERS_HASH_KEY, order.id(), toJson(order));
    }

    @Override
    public Optional<DeliveryOrder> findOrder(String orderId) {
        Object raw = redisTemplate.opsForHash().get(ORDERS_HASH_KEY, orderId);
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(fromJson(raw.toString(), DeliveryOrder.class));
    }

    @Override
    public List<DeliveryOrder> findAllOrders() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(ORDERS_HASH_KEY);
        return entries.values().stream()
                .map(val -> fromJson(val.toString(), DeliveryOrder.class))
                .sorted(Comparator.comparing(DeliveryOrder::createdAt).reversed())
                .toList();
    }

    @Override
    public void clearAll() {
        redisTemplate.delete(RIDER_GEO_KEY);
        redisTemplate.delete(RIDERS_HASH_KEY);
        redisTemplate.delete(ORDERS_HASH_KEY);
        Set<String> lockKeys = redisTemplate.keys(LOCK_PREFIX + "*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize state for Redis", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize state from Redis", e);
        }
    }
}

