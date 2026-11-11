package com.swiftfleet.model;

import java.time.Instant;

public record InspectorLogEntry(
        long seq,
        String category,
        String command,
        String detail,
        long latencyMs,
        String backendMode,
        Instant timestamp
) {
}

