package com.swiftfleet.model;

import java.time.Instant;

public record DispatchAlertEvent(
        String orderId,
        String targetRiderId,
        String customerName,
        String customerAddress,
        String itemsSummary,
        double storeLat,
        double storeLng,
        double dropLat,
        double dropLng,
        double distanceToStoreKm,
        Instant timestamp
) {
}

