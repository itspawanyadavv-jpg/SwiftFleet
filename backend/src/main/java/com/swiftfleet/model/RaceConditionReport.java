package com.swiftfleet.model;

import java.util.List;

public record RaceConditionReport(
        String orderId,
        String winnerRiderId,
        String winnerRiderName,
        String lockMechanism,
        long elapsedMs,
        List<AttemptResult> attempts
) {
    public record AttemptResult(
            String riderId,
            String riderName,
            int httpStatus,
            boolean won,
            String message
    ) {
    }
}

