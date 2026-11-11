package com.swiftfleet.service;

import com.swiftfleet.model.InspectorLogEntry;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures Redis Geo, distributed lock, OTP, and STOMP WebSocket operations
 * and streams them live to /topic/inspector for the UI Architecture Inspector.
 */
@Service
public class InspectorService {

    private static final int MAX_ENTRIES = 120;

    private final SimpMessagingTemplate messagingTemplate;
    private final Deque<InspectorLogEntry> history = new ArrayDeque<>(MAX_ENTRIES);
    private final AtomicLong sequence = new AtomicLong(0);

    public InspectorService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public InspectorLogEntry record(String category, String command, String detail, long latencyMs, String backendMode) {
        InspectorLogEntry entry = new InspectorLogEntry(
                sequence.incrementAndGet(),
                category,
                command,
                detail,
                Math.max(0L, latencyMs),
                backendMode,
                Instant.now()
        );
        synchronized (history) {
            if (history.size() >= MAX_ENTRIES) {
                history.removeFirst();
            }
            history.addLast(entry);
        }
        messagingTemplate.convertAndSend("/topic/inspector", entry);
        return entry;
    }

    public List<InspectorLogEntry> recentEntries() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void clear() {
        synchronized (history) {
            history.clear();
        }
    }
}

