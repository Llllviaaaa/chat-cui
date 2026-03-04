package com.chatcui.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.chatcui.gateway.auth.AuthService;
import com.chatcui.gateway.auth.ErrorResponseFactory;
import com.chatcui.gateway.auth.FailureCooldownPolicy;
import com.chatcui.gateway.auth.ReplayGuard;
import com.chatcui.gateway.auth.model.AuthCredentialRecord;
import com.chatcui.gateway.auth.model.AuthRequest;
import com.chatcui.gateway.observability.BridgeMetricsRegistry;
import com.chatcui.gateway.observability.FailureClass;
import com.chatcui.gateway.ws.WsAuthHandshakeInterceptor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WsAuthFailureIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");

    @Test
    void invalidSignatureUsesSharedAuthV1CodeAndCloseReason() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BridgeMetricsRegistry metricsRegistry = new BridgeMetricsRegistry(meterRegistry);
        WsAuthHandshakeInterceptor interceptor = buildInterceptor(true, metricsRegistry);
        AuthRequest invalid = new AuthRequest(
                "ak_live_1234",
                "tenant-a",
                "client-a",
                NOW.getEpochSecond(),
                "nonce-1",
                "session-1",
                Base64.getEncoder().encodeToString("bad".getBytes(StandardCharsets.UTF_8)),
                "trace-1");

        WsAuthHandshakeInterceptor.HandshakeDecision decision = interceptor.beforeHandshake(invalid);

        assertFalse(decision.accepted());
        assertEquals(4401, decision.closeCode());
        assertEquals("AUTH_V1_INVALID_SIGNATURE", decision.errorResponse().error_code());
        assertNotNull(decision.errorResponse().next_action());
        assertEquals(1.0, counterCount(meterRegistry, "invalid_signature", true));
    }

    @Test
    void permissionDeniedUses403FamilyCloseCode() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BridgeMetricsRegistry metricsRegistry = new BridgeMetricsRegistry(meterRegistry);
        WsAuthHandshakeInterceptor interceptor = buildInterceptor(false, metricsRegistry);
        AuthRequest request = new AuthRequest(
                "ak_live_1234",
                "tenant-a",
                "client-a",
                NOW.getEpochSecond(),
                "nonce-2",
                "session-2",
                Base64.getEncoder().encodeToString("bad".getBytes(StandardCharsets.UTF_8)),
                "trace-2");

        WsAuthHandshakeInterceptor.HandshakeDecision decision = interceptor.beforeHandshake(request);

        assertFalse(decision.accepted());
        assertEquals(4403, decision.closeCode());
        assertEquals("AUTH_V1_PERMISSION_DENIED", decision.errorResponse().error_code());
        assertEquals(1.0, counterCount(meterRegistry, "permission_denied", false));
    }

    private WsAuthHandshakeInterceptor buildInterceptor(boolean permitted, BridgeMetricsRegistry metricsRegistry) {
        Map<String, AuthCredentialRecord> credentials = Map.of(
                "tenant-a|client-a|ak_live_1234",
                new AuthCredentialRecord(
                        "tenant-a",
                        "client-a",
                        "ak_live_1234",
                        "secret-a",
                        "ACTIVE",
                        permitted));

        AuthService.CredentialStore store =
                (tenantId, clientId, ak) -> Optional.ofNullable(credentials.get(tenantId + "|" + clientId + "|" + ak));
        AuthService service = new AuthService(
                store,
                new ReplayGuard(),
                new FailureCooldownPolicy(Duration.ofSeconds(30), Duration.ofMinutes(5)),
                new AuthService.Policy(Duration.ofMinutes(15), Duration.ofMinutes(5)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new WsAuthHandshakeInterceptor(service, new ErrorResponseFactory(), metricsRegistry);
    }

    private double counterCount(
            SimpleMeterRegistry registry,
            String outcome,
            boolean retryable) {
        return registry.find("chatcui.gateway.auth.outcomes")
                .tags(
                        "component",
                        "gateway.auth",
                        "outcome",
                        outcome,
                        "failure_class",
                        FailureClass.AUTH.value(),
                        "retryable",
                        Boolean.toString(retryable))
                .counter()
                .count();
    }
}
