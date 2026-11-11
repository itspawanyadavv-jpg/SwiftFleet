package com.swiftfleet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;

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
    public String[] parsedOriginPatterns() {
        if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank()) {
            return new String[]{"*"};
        }
        String[] origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .map(s -> s.replaceAll("/+$", ""))
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        return origins.length == 0 ? new String[]{"*"} : origins;
    }
}
