package com.chatcui.gateway.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReplayGuard {
    private final Map<String, Instant> nonceExpirations = new ConcurrentHashMap<>();

    public boolean registerIfFirst(String replayKey, Duration ttl, Instant now) {
        cleanup(now);
        Instant expiration = now.plus(ttl);
        return nonceExpirations.putIfAbsent(replayKey, expiration) == null;
    }

    private void cleanup(Instant now) {
        nonceExpirations.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
