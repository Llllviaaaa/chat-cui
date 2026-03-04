package com.chatcui.gateway.routing;

import java.time.Instant;
import java.util.Objects;

public record RouteOwnershipRecord(
        String tenantId,
        String sessionId,
        long routeVersion,
        String skillOwner,
        String gatewayOwner,
        String fencedOwner,
        Instant updatedAt) {

    public static final String FIELD_TENANT_ID = "tenant_id";
    public static final String FIELD_SESSION_ID = "session_id";
    public static final String FIELD_ROUTE_VERSION = "route_version";
    public static final String FIELD_SKILL_OWNER = "skill_owner";
    public static final String FIELD_GATEWAY_OWNER = "gateway_owner";
    public static final String FIELD_FENCED_OWNER = "fenced_owner";

    public RouteOwnershipRecord {
        tenantId = normalizeRequired(tenantId, FIELD_TENANT_ID);
        sessionId = normalizeRequired(sessionId, FIELD_SESSION_ID);
        if (routeVersion < 0) {
            throw new IllegalArgumentException(FIELD_ROUTE_VERSION + " must be >= 0");
        }
        skillOwner = normalizeRequired(skillOwner, FIELD_SKILL_OWNER);
        gatewayOwner = normalizeRequired(gatewayOwner, FIELD_GATEWAY_OWNER);
        fencedOwner = normalizeOptional(fencedOwner);
        updatedAt = Objects.requireNonNullElseGet(updatedAt, Instant::now);
    }

    public RouteOwnershipRecord transferTo(
            long nextRouteVersion,
            String nextSkillOwner,
            String nextGatewayOwner,
            String nextFencedOwner,
            Instant transferTime) {
        return new RouteOwnershipRecord(
                tenantId,
                sessionId,
                nextRouteVersion,
                nextSkillOwner,
                nextGatewayOwner,
                nextFencedOwner,
                transferTime);
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
