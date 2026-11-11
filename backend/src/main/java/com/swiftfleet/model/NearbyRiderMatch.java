package com.swiftfleet.model;

public record NearbyRiderMatch(
        String riderId,
        String riderName,
        String vehicleNumber,
        RiderStatus status,
        double distanceKm,
        double lat,
        double lng
) {
}

