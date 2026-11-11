package com.swiftfleet.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftfleet.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Selects real Redis store when reachable (or forced via FLEET_STORE_MODE=redis),
 * and cleanly degrades to thread-safe InMemoryFleetGeoLockStore when running locally without Redis.
 */
@Configuration
public class FleetStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(FleetStoreConfig.class);

    @Bean
    public FleetGeoLockStore fleetGeoLockStore(
            AppProperties properties,
            StringRedisTemplate stringRedisTemplate,
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper
    ) {
        String mode = properties.storeMode() == null ? "auto" : properties.storeMode().trim().toLowerCase();

        if ("memory".equals(mode)) {
            log.info("SwiftFleet starting in IN_MEMORY_FALLBACK mode (FLEET_STORE_MODE=memory)");
            return new InMemoryFleetGeoLockStore();
        }

        if ("redis".equals(mode)) {
            log.info("SwiftFleet starting in strict REDIS_LIVE mode");
            return new RedisFleetGeoLockStore(stringRedisTemplate, objectMapper);
        }

        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                log.info("Connected to Redis successfully (PING={}) — using REDIS_LIVE mode", pong);
                return new RedisFleetGeoLockStore(stringRedisTemplate, objectMapper);
            }
        } catch (Exception ex) {
            log.warn("Redis unavailable ({}). Falling back to IN_MEMORY_FALLBACK mode.", ex.getMessage());
        }

        return new InMemoryFleetGeoLockStore();
    }
}

