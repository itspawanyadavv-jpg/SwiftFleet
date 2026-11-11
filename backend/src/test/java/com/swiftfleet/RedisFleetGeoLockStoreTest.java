package com.swiftfleet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftfleet.store.RedisFleetGeoLockStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisFleetGeoLockStoreTest {

    @SuppressWarnings("unchecked")
    @Test
    void usesLongitudeLatitudePointOrderAndAtomicSetNxExWithLuaUnlock() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        GeoOperations<String, String> geoOps = mock(GeoOperations.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);

        when(template.opsForGeo()).thenReturn(geoOps);
        when(template.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("lock:order:ORD-101", "rdr-1", Duration.ofSeconds(30))).thenReturn(true);
        when(valueOps.setIfAbsent("lock:order:ORD-101", "rdr-2", Duration.ofSeconds(30))).thenReturn(false);
        when(template.execute(any(RedisScript.class), eq(List.of("lock:order:ORD-101")), eq("rdr-1"))).thenReturn(1L);

        RedisFleetGeoLockStore store = new RedisFleetGeoLockStore(template, new ObjectMapper().findAndRegisterModules());
        assertEquals("REDIS_LIVE", store.backendMode());

        store.upsertRiderGeo("rdr-1", 28.646705, 77.444478);
        verify(geoOps).add(RedisFleetGeoLockStore.RIDER_GEO_KEY, new Point(77.444478, 28.646705), "rdr-1");

        assertTrue(store.tryAcquireOrderLock("ORD-101", "rdr-1", 30));
        assertFalse(store.tryAcquireOrderLock("ORD-101", "rdr-2", 30));
        assertTrue(store.releaseOrderLock("ORD-101", "rdr-1"));

        store.findNearbyRiderIds(28.646705, 77.444478, 3.0);
        verify(geoOps).search(
                eq(RedisFleetGeoLockStore.RIDER_GEO_KEY),
                any(),
                any(Distance.class),
                any()
        );
    }
}

