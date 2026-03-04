package com.chatcui.gateway.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
}
