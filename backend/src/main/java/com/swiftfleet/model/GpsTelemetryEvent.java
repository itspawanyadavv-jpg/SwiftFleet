package com.swiftfleet.model;

import java.time.Instant;

public record GpsTelemetryEvent(
        String riderId,
        String orderId,
        double lat,
        double lng,
        double speedKmh,
        double bearingDeg,
        double remainingKm,
        int etaMinutes,
        OrderStatus orderStatus,
        boolean insideDeliveryGeofence,
        double distanceToDropMeters,
        Instant timestamp
) {
}

