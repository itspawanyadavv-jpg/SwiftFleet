package com.swiftfleet.model;

import java.time.Instant;
import java.util.List;

public record DeliveryOrder(
        String id,
        String customerName,
        String customerAddress,
        String itemsSummary,
        double storeLat,
        double storeLng,
        double dropLat,
        double dropLng,
        OrderStatus status,
        String assignedRiderId,
        String assignedRiderName,
        String deliveryOtp,
        int otpAttempts,
        boolean otpLocked,
        double remainingKm,
        int etaMinutes,
        String routeSource,
        List<LatLng> routePolyline,
        List<NearbyRiderMatch> candidateRiders,
        Instant createdAt,
        Instant assignedAt,
        Instant pickedUpAt,
        Instant deliveredAt
) {
}

