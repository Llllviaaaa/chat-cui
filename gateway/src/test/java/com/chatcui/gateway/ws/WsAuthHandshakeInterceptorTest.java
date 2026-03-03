package com.chatcui.gateway.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.auth.AuthService;
import com.chatcui.gateway.auth.ErrorResponseFactory;
import com.chatcui.gateway.auth.FailureCooldownPolicy;
import com.chatcui.gateway.auth.ReplayGuard;
import com.chatcui.gateway.auth.model.AuthCredentialRecord;
import com.chatcui.gateway.auth.model.AuthRequest;
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

class WsAuthHandshakeInterceptorTest {
    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");

    @Test
    void rejectsInvalidSignatureWithMappedCloseCode() {
        WsAuthHandshakeInterceptor interceptor = build(false);
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
    }

    @Test
    void acceptsValidRequestAndAttachesPrincipal() {
        WsAuthHandshakeInterceptor interceptor = build(false);
        AuthRequest valid = signed("nonce-2", "session-2", NOW.getEpochSecond());

        WsAuthHandshakeInterceptor.HandshakeDecision decision = interceptor.beforeHandshake(valid);

        assertTrue(decision.accepted());
        assertEquals("tenant-a", decision.principal().tenantId());
    }

    private WsAuthHandshakeInterceptor build(boolean disabled) {
        Map<String, AuthCredentialRecord> credentials = Map.of(
                "tenant-a|client-a|ak_live_1234",
                new AuthCredentialRecord(
                        "tenant-a",
                        "client-a",
                        "ak_live_1234",
                        "secret-a",
                        disabled ? "DISABLED" : "ACTIVE",
                        true));
        AuthService.CredentialStore store =
                (tenantId, clientId, ak) -> Optional.ofNullable(credentials.get(tenantId + "|" + clientId + "|" + ak));
        AuthService authService = new AuthService(
                store,
                new ReplayGuard(),
                new FailureCooldownPolicy(Duration.ofSeconds(30), Duration.ofMinutes(5)),
                new AuthService.Policy(Duration.ofMinutes(15), Duration.ofMinutes(5)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new WsAuthHandshakeInterceptor(authService, new ErrorResponseFactory());
    }

    private AuthRequest signed(String nonce, String sessionId, long timestamp) {
        AuthRequest draft = new AuthRequest(
                "ak_live_1234",
                "tenant-a",
                "client-a",
                timestamp,
                nonce,
                sessionId,
                "placeholder",
                "trace-" + sessionId);
        String signature = sign(AuthService.canonicalPayload(draft), "secret-a");
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
}
