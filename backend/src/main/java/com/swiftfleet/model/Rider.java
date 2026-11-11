package com.swiftfleet.model;

import java.time.Instant;

public record Rider(
        String id,
        String name,
        String vehicleNumber,
        String phone,
        RiderStatus status,
        double lat,
        double lng,
        double speedKmh,
        double bearingDeg,
        String activeOrderId,
        Instant updatedAt
) {
    public Rider withStatusAndOrder(RiderStatus nextStatus, String nextOrderId, Instant at) {
        return new Rider(id, name, vehicleNumber, phone, nextStatus, lat, lng, speedKmh, bearingDeg, nextOrderId, at);
    }

    public Rider withLocation(double nextLat, double nextLng, double nextSpeedKmh, double nextBearingDeg, Instant at) {
        return new Rider(id, name, vehicleNumber, phone, status, nextLat, nextLng, nextSpeedKmh, nextBearingDeg, activeOrderId, at);
    }
}

