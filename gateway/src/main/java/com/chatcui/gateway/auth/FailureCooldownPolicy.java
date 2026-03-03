package com.chatcui.gateway.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

public class FailureCooldownPolicy {
    private final Duration baseCooldown;
    private final Duration maxCooldown;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public FailureCooldownPolicy(Duration baseCooldown, Duration maxCooldown) {
        this.baseCooldown = baseCooldown;
        this.maxCooldown = maxCooldown;
    }

    public OptionalInt retryAfterSeconds(String key, Instant now) {
        Entry entry = entries.get(key);
        if (entry == null || entry.blockedUntil == null || !entry.blockedUntil.isAfter(now)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) Duration.between(now, entry.blockedUntil).toSeconds());
    }

    public void recordFailure(String key, Instant now) {
        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry());
        entry.failures++;
        long multiplier = 1L << Math.max(0, entry.failures - 1);
        Duration raw = baseCooldown.multipliedBy(multiplier);
        Duration cooldown = raw.compareTo(maxCooldown) > 0 ? maxCooldown : raw;
        entry.blockedUntil = now.plus(cooldown);
    }

    public void clear(String key) {
        entries.remove(key);
    }

    private static final class Entry {
        int failures = 0;
        Instant blockedUntil;
    }
}
