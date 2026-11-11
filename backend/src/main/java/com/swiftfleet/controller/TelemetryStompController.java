package com.swiftfleet.controller;

import com.swiftfleet.service.RiderService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * Handles live GPS telemetry frames sent directly by riders over STOMP WebSockets (/app/rider/location).
 */
@Controller
public class TelemetryStompController {

    public record StompLocationPayload(
            String riderId,
            double lat,
            double lng,
            Double speedKmh,
            Double bearingDeg
    ) {
    }

    private final RiderService riderService;

    public TelemetryStompController(RiderService riderService) {
        this.riderService = riderService;
    }

    @MessageMapping("/rider/location")
    public void onRiderLocation(StompLocationPayload payload) {
        if (payload == null || payload.riderId() == null || payload.riderId().isBlank()) {
            return;
        }
        riderService.updateLocation(
                payload.riderId(),
                payload.lat(),
                payload.lng(),
                payload.speedKmh(),
                payload.bearingDeg(),
                true
        );
    }
}

