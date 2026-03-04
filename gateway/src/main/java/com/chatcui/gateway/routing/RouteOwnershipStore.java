package com.chatcui.gateway.routing;

import java.time.Duration;
import java.util.Optional;

public interface RouteOwnershipStore {
    Optional<RouteOwnershipRecord> load(String tenantId, String sessionId);

    RouteOwnershipRecord upsert(RouteOwnershipRecord record, Duration ttl);

    RouteCasResult casTransfer(
            String tenantId,
            String sessionId,
            long expectedRouteVersion,
            String newSkillOwner,
            String newGatewayOwner,
            String fencedOwner,
            Duration ttl);
}
