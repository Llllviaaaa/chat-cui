package com.chatcui.gateway.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.auth.model.AuthCredentialRecord;
import com.chatcui.gateway.auth.model.AuthFailureCode;
import com.chatcui.gateway.auth.model.AuthRequest;
import com.chatcui.gateway.auth.model.AuthResult;
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

class AuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");

    @Test
    void authenticatesValidSignedRequest() {
        AuthService service = buildService(false);
        AuthRequest request = signedRequest(NOW.getEpochSecond(), "nonce-1", "session-1", "secret-a");

        AuthResult result = service.authenticate(request);

        assertTrue(result.isSuccess());
        assertEquals("tenant-a", result.principal().tenantId());
    }

    @Test
    void missingCredentialDataFailsDeterministically() {
        AuthService service = buildService(false);
        AuthRequest request = new AuthRequest("", "tenant-a", "client-a", NOW.getEpochSecond(), "nonce", "session", "sig", "trace");

        AuthResult result = service.authenticate(request);

        assertEquals(AuthFailureCode.AUTH_V1_MISSING_CREDENTIAL, result.failureCode());
    }

    @Test
    void invalidSignatureFails() {
        AuthService service = buildService(false);
        AuthRequest request = new AuthRequest(
                "ak_live_1234",
                "tenant-a",
                "client-a",
                NOW.getEpochSecond(),
                "nonce-2",
                "session-2",
                Base64.getEncoder().encodeToString("wrong".getBytes(StandardCharsets.UTF_8)),
                "trace-2");

        AuthResult result = service.authenticate(request);

        assertEquals(AuthFailureCode.AUTH_V1_INVALID_SIGNATURE, result.failureCode());
    }

    @Test
    void replayNonceRejected() {
        AuthService service = buildService(false);
        AuthRequest request = signedRequest(NOW.getEpochSecond(), "nonce-3", "session-3", "secret-a");

        AuthResult first = service.authenticate(request);
        AuthResult second = service.authenticate(request);

        assertTrue(first.isSuccess());
        assertEquals(AuthFailureCode.AUTH_V1_REPLAY_DETECTED, second.failureCode());
    }

    @Test
    void skewOutOfWindowRejected() {
        AuthService service = buildService(false);
        AuthRequest request = signedRequest(NOW.minusSeconds(901).getEpochSecond(), "nonce-4", "session-4", "secret-a");

        AuthResult result = service.authenticate(request);

        assertEquals(AuthFailureCode.AUTH_V1_TIMESTAMP_OUT_OF_WINDOW, result.failureCode());
    }

    @Test
    void disabledCredentialRejected() {
        AuthService service = buildService(true);
        AuthRequest request = signedRequest(NOW.getEpochSecond(), "nonce-5", "session-5", "secret-a");

        AuthResult result = service.authenticate(request);

        assertEquals(AuthFailureCode.AUTH_V1_CREDENTIAL_DISABLED, result.failureCode());
    }

    @Test
    void cooldownActivatedAfterRepeatedFailures() {
        AuthService service = buildService(false);
        AuthRequest bad = new AuthRequest(
                "ak_live_1234",
                "tenant-a",
                "client-a",
                NOW.getEpochSecond(),
                "nonce-bad",
                "session-bad",
                Base64.getEncoder().encodeToString("bad".getBytes(StandardCharsets.UTF_8)),
                "trace-bad");

        service.authenticate(bad);
        AuthResult result = service.authenticate(bad);

        assertEquals(AuthFailureCode.AUTH_V1_COOLDOWN_ACTIVE, result.failureCode());
        assertTrue(result.retryAfterSeconds() != null && result.retryAfterSeconds() > 0);
    }

    private AuthService buildService(boolean disabled) {
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

        ReplayGuard replayGuard = new ReplayGuard();
        FailureCooldownPolicy cooldownPolicy = new FailureCooldownPolicy(Duration.ofSeconds(30), Duration.ofMinutes(5));
        AuthService.Policy policy = new AuthService.Policy(Duration.ofMinutes(15), Duration.ofMinutes(5));
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new AuthService(store, replayGuard, cooldownPolicy, policy, fixedClock);
    }

    private AuthRequest signedRequest(long timestamp, String nonce, String sessionId, String secret) {
        AuthRequest request = new AuthRequest(
                "ak_live_1234",
                "tenant-a",
                "client-a",
                timestamp,
                nonce,
                sessionId,
                "placeholder",
                "trace-" + sessionId);
        String signature = sign(AuthService.canonicalPayload(request), secret);
        return new AuthRequest(
                request.ak(),
                request.tenantId(),
                request.clientId(),
                request.timestamp(),
                request.nonce(),
                request.sessionId(),
                signature,
                request.traceId());
    }

    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
