package com.app.accounting.security.payload.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ReplayProtectionService {

    // Default window: 5 minutes (300,000 ms)
    @Value("${app.security.payload.timestamp-tolerance-ms:300000}")
    private long toleranceWindowMs = 300000L;

    private final Map<String, Long> seenRequestIds = new ConcurrentHashMap<>();

    public boolean validateRequest(String requestId, Long timestamp) {
        if (requestId == null || requestId.isBlank() || timestamp == null) {
            return false;
        }

        long now = Instant.now().toEpochMilli();
        long diff = Math.abs(now - timestamp);

        if (diff > toleranceWindowMs) {
            log.warn("Payload rejected: timestamp outside valid window. RequestId: {}, DiffMs: {}", requestId, diff);
            return false;
        }

        // Periodic eviction of expired request IDs
        cleanupExpiredEntries(now);

        // Put request ID with expiry timestamp
        long expiresAt = now + toleranceWindowMs;
        Long existing = seenRequestIds.putIfAbsent(requestId, expiresAt);
        if (existing != null) {
            log.warn("Payload rejected: duplicate/replayed requestId detected: {}", requestId);
            return false;
        }

        return true;
    }

    private void cleanupExpiredEntries(long now) {
        if (seenRequestIds.size() > 5000) {
            seenRequestIds.entrySet().removeIf(entry -> entry.getValue() < now);
        }
    }
}
