package com.swiftfleet.model;

public record StoreConfig(
        String storeName,
        double storeLat,
        double storeLng,
        double dispatchRadiusKm,
        double deliveryGeofenceMeters,
        long orderLockTtlSeconds,
        int otpMaxAttempts,
        String storeMode,
        boolean drcSynced,
        String drcApiUrl
) {
}

