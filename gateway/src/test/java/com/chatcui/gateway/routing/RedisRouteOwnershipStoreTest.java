package com.chatcui.gateway.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedisRouteOwnershipStoreTest {
    private static final Duration ROUTE_TTL = Duration.ofMinutes(15);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void casTransferUpdatesOwnersIncrementsVersionAndPersistsFence() {
        InMemoryRedisExecutor redis = new InMemoryRedisExecutor();
        RedisRouteOwnershipStore store = new RedisRouteOwnershipStore(redis, FIXED_CLOCK);

        store.upsert(
                new RouteOwnershipRecord(
                        "tenant-a",
                        "session-a",
                        7L,
                        "skill-owner-a",
                        "gateway-owner-a",
                        null,
                        Instant.parse("2026-03-04T11:59:30Z")),
                ROUTE_TTL);

        RouteCasResult result = store.casTransfer(
                "tenant-a",
                "session-a",
                7L,
                "skill-owner-b",
                "gateway-owner-b",
                "gateway-owner-a",
                ROUTE_TTL);

        assertTrue(result.applied());
        assertEquals(8L, result.record().routeVersion());
        assertEquals("skill-owner-b", result.record().skillOwner());
        assertEquals("gateway-owner-b", result.record().gatewayOwner());
        assertEquals("gateway-owner-a", result.record().fencedOwner());
        assertEquals(
                "gateway-owner-a",
                store.load("tenant-a", "session-a").orElseThrow().fencedOwner(),
                "fence should be visible immediately after CAS success");
    }

    @Test
    void casTransferConflictReturnsCurrentVersionAndOwners() {
        InMemoryRedisExecutor redis = new InMemoryRedisExecutor();
        RedisRouteOwnershipStore store = new RedisRouteOwnershipStore(redis, FIXED_CLOCK);

        store.upsert(
                new RouteOwnershipRecord(
                        "tenant-a",
                        "session-a",
                        10L,
                        "skill-owner-a",
                        "gateway-owner-a",
                        null,
                        Instant.parse("2026-03-04T11:59:00Z")),
                ROUTE_TTL);

        RouteCasResult result = store.casTransfer(
                "tenant-a",
                "session-a",
                9L,
                "skill-owner-b",
                "gateway-owner-b",
                "gateway-owner-a",
                ROUTE_TTL);

        assertTrue(result.versionConflict());
        assertFalse(result.applied());
        assertEquals(9L, result.expectedRouteVersion());
        assertEquals(10L, result.currentRouteVersion());
        assertEquals("skill-owner-a", result.record().skillOwner());
        assertEquals("gateway-owner-a", result.record().gatewayOwner());
    }

    @Test
    void casTransferReturnsMissingWhenRouteIsAbsent() {
        InMemoryRedisExecutor redis = new InMemoryRedisExecutor();
        RedisRouteOwnershipStore store = new RedisRouteOwnershipStore(redis, FIXED_CLOCK);

        RouteCasResult result = store.casTransfer(
                "tenant-a",
                "missing-session",
                1L,
                "skill-owner-b",
                "gateway-owner-b",
                "gateway-owner-a",
                ROUTE_TTL);

        assertEquals(RouteCasResult.Status.MISSING, result.status());
        assertFalse(result.applied());
    }

    private static final class InMemoryRedisExecutor implements RedisRouteOwnershipStore.RedisExecutor {
        private final Map<String, Map<String, String>> hashes = new HashMap<>();

        @Override
        public Map<String, String> hgetall(String key) {
            return new HashMap<>(hashes.getOrDefault(key, Map.of()));
        }

        @Override
        public void hset(String key, Map<String, String> fields) {
            hashes.computeIfAbsent(key, ignored -> new HashMap<>()).putAll(fields);
        }

        @Override
        public void expire(String key, long ttlSeconds) {
        }

        @Override
        public List<Object> eval(String script, String key, String... args) {
            Map<String, String> row = hashes.get(key);
            if (row == null || row.isEmpty() || !row.containsKey(RouteOwnershipRecord.FIELD_ROUTE_VERSION)) {
                return List.of("missing");
            }

            long currentVersion = Long.parseLong(row.get(RouteOwnershipRecord.FIELD_ROUTE_VERSION));
            long expectedVersion = Long.parseLong(args[0]);
            if (currentVersion != expectedVersion) {
                return List.of(
                        "conflict",
                        Long.toString(currentVersion),
                        row.getOrDefault(RouteOwnershipRecord.FIELD_SKILL_OWNER, ""),
                        row.getOrDefault(RouteOwnershipRecord.FIELD_GATEWAY_OWNER, ""),
                        row.getOrDefault(RouteOwnershipRecord.FIELD_FENCED_OWNER, ""),
                        row.getOrDefault(RedisRouteOwnershipStore.FIELD_UPDATED_AT_EPOCH_MS, "0"));
            }

            long nextVersion = currentVersion + 1;
            row.put(RouteOwnershipRecord.FIELD_TENANT_ID, args[1]);
            row.put(RouteOwnershipRecord.FIELD_SESSION_ID, args[2]);
            row.put(RouteOwnershipRecord.FIELD_ROUTE_VERSION, Long.toString(nextVersion));
            row.put(RouteOwnershipRecord.FIELD_SKILL_OWNER, args[3]);
            row.put(RouteOwnershipRecord.FIELD_GATEWAY_OWNER, args[4]);
            row.put(RouteOwnershipRecord.FIELD_FENCED_OWNER, args[5]);
            row.put(RedisRouteOwnershipStore.FIELD_UPDATED_AT_EPOCH_MS, args[6]);

            return List.of(
                    "applied",
                    Long.toString(nextVersion),
                    args[3],
                    args[4],
                    args[5],
                    args[6]);
        }
    }
}
