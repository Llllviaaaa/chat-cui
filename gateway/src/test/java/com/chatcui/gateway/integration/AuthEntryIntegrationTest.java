package com.chatcui.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.auth.AuthService;
import com.chatcui.gateway.auth.ErrorResponseFactory;
import com.chatcui.gateway.auth.FailureCooldownPolicy;
import com.chatcui.gateway.auth.ReplayGuard;
import com.chatcui.gateway.auth.model.AuthCredentialRecord;
import com.chatcui.gateway.auth.model.AuthRequest;
import com.chatcui.gateway.http.AuthEntryInterceptor;
import com.chatcui.gateway.observability.BridgeMetricsRegistry;
import com.chatcui.gateway.observability.FailureClass;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AuthEntryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");

    @Test
    void validAuthAllowsEntry() {
        AuthEntryInterceptor interceptor = buildInterceptor(true, true, BridgeMetricsRegistry.noop());
        AuthRequest request = signedRequest("nonce-1", "session-1", NOW.getEpochSecond(), "secret-a");

        AuthEntryInterceptor.EntryDecision decision = interceptor.preHandle(request);

        assertTrue(decision.allowed());
        assertEquals(200, decision.statusCode());
    }

    @Test
    void missingMetadataRejectedWithDeterministicCode() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BridgeMetricsRegistry metricsRegistry = new BridgeMetricsRegistry(meterRegistry);
        AuthEntryInterceptor interceptor = buildInterceptor(true, true, metricsRegistry);
        AuthRequest invalid = new AuthRequest("", "tenant-a", "client-a", NOW.getEpochSecond(), "nonce", "session", "sig", "trace");

        AuthEntryInterceptor.EntryDecision decision = interceptor.preHandle(invalid);

        assertEquals(false, decision.allowed());
        assertEquals(400, decision.statusCode());
        assertEquals("AUTH_V1_MISSING_CREDENTIAL", decision.errorResponse().error_code());
        assertEquals(1.0, counterCount(
                meterRegistry,
                "missing_credential",
                true));
    }

    @Test
    void replayRequestRejected() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BridgeMetricsRegistry metricsRegistry = new BridgeMetricsRegistry(meterRegistry);
        AuthEntryInterceptor interceptor = buildInterceptor(true, true, metricsRegistry);
        AuthRequest request = signedRequest("nonce-2", "session-2", NOW.getEpochSecond(), "secret-a");

        AuthEntryInterceptor.EntryDecision first = interceptor.preHandle(request);
        AuthEntryInterceptor.EntryDecision second = interceptor.preHandle(request);

        assertTrue(first.allowed());
        assertEquals(false, second.allowed());
        assertEquals("AUTH_V1_REPLAY_DETECTED", second.errorResponse().error_code());
        assertEquals(1.0, counterCount(
                meterRegistry,
                "replay_detected",
                true));
    }

    @Test
    void cooldownReturnedAfterRepeatedInvalidSignature() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BridgeMetricsRegistry metricsRegistry = new BridgeMetricsRegistry(meterRegistry);
        AuthEntryInterceptor interceptor = buildInterceptor(true, true, metricsRegistry);
        AuthRequest invalid = new AuthRequest(
                "ak_live_1234",
                "tenant-a",
                "client-a",
                NOW.getEpochSecond(),
                "nonce-3",
                "session-3",
                Base64.getEncoder().encodeToString("bad".getBytes(StandardCharsets.UTF_8)),
                "trace-3");

        interceptor.preHandle(invalid);
        AuthEntryInterceptor.EntryDecision decision = interceptor.preHandle(invalid);

        assertEquals(false, decision.allowed());
        assertEquals("AUTH_V1_COOLDOWN_ACTIVE", decision.errorResponse().error_code());
        assertTrue(decision.errorResponse().retry_after() != null && decision.errorResponse().retry_after() > 0);
        assertEquals(1.0, counterCount(
                meterRegistry,
                "cooldown_active",
                true));
    }

    private AuthEntryInterceptor buildInterceptor(
            boolean active,
            boolean permitted,
            BridgeMetricsRegistry metricsRegistry) {
        Map<String, AuthCredentialRecord> credentials = Map.of(
                "tenant-a|client-a|ak_live_1234",
                new AuthCredentialRecord(
                        "tenant-a",
                        "client-a",
                        "ak_live_1234",
                        "secret-a",
                        active ? "ACTIVE" : "DISABLED",
                        permitted));

        AuthService.CredentialStore store =
                (tenantId, clientId, ak) -> Optional.ofNullable(credentials.get(tenantId + "|" + clientId + "|" + ak));

        AuthService service = new AuthService(
                store,
                new ReplayGuard(),
                new FailureCooldownPolicy(Duration.ofSeconds(30), Duration.ofMinutes(5)),
                new AuthService.Policy(Duration.ofMinutes(15), Duration.ofMinutes(5)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new AuthEntryInterceptor(service, new ErrorResponseFactory(), metricsRegistry);
    }

    private AuthRequest signedRequest(String nonce, String sessionId, long timestamp, String secret) {
        AuthRequest draft = new AuthRequest(
                "ak_live_1234",
                "tenant-a",
                "client-a",
                timestamp,
                nonce,
                sessionId,
                "placeholder",
                "trace-" + sessionId);
        String signature = sign(AuthService.canonicalPayload(draft), secret);
        return new AuthRequest(
                draft.ak(),
                draft.tenantId(),
                draft.clientId(),
                draft.timestamp(),
                draft.nonce(),
                draft.sessionId(),
                signature,
                draft.traceId());
    }

    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
