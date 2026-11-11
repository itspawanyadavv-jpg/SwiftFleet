package com.swiftfleet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "swiftfleet")
public record AppProperties(
        String storeName,
        double storeLat,
        double storeLng,
        double dispatchRadiusKm,
        String drcStoreApiUrl,
        String storeMode,
        long orderLockTtlSeconds,
        double deliveryGeofenceMeters,
        int otpMaxAttempts,
        String corsAllowedOrigins
) {
}

