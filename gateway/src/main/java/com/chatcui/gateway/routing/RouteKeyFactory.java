package com.chatcui.gateway.routing;

public final class RouteKeyFactory {
    public static final String ROUTE_KEY_PREFIX = "chatcui:route:";

    private RouteKeyFactory() {
    }

    public static String routeKey(String tenantId, String sessionId) {
        return ROUTE_KEY_PREFIX + routeHashTag(tenantId, sessionId);
    }

    static String routeHashTag(String tenantId, String sessionId) {
        String normalizedTenant = normalize(tenantId, "tenant_id");
        String normalizedSession = normalize(sessionId, "session_id");
        return "{" + normalizedTenant + ":" + normalizedSession + "}";
    }

    private static String normalize(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
