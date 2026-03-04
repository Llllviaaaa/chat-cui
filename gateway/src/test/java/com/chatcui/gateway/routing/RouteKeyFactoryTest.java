package com.chatcui.gateway.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RouteKeyFactoryTest {

    @Test
    void buildsClusterSafeRouteKeyFromTenantAndSession() {
        String key = RouteKeyFactory.routeKey("tenant-a", "session-a");

        assertEquals("chatcui:route:{tenant-a:session-a}", key);
    }

    @Test
    void trimsIdentifiersBeforeFormattingKey() {
        String key = RouteKeyFactory.routeKey(" tenant-a ", " session-a ");

        assertEquals("chatcui:route:{tenant-a:session-a}", key);
    }

    @Test
    void rejectsBlankTenantOrSessionIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> RouteKeyFactory.routeKey(" ", "session-a"));
        assertThrows(IllegalArgumentException.class, () -> RouteKeyFactory.routeKey("tenant-a", ""));
    }
}
