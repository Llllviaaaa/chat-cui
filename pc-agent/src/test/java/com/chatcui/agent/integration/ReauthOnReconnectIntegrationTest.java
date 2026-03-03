package com.chatcui.agent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.agent.auth.WindowsKeystoreCredentialProvider;
import com.chatcui.agent.config.AuthConfigLoader;
import com.chatcui.gateway.auth.AuthService;
import com.chatcui.gateway.auth.ErrorResponseFactory;
import com.chatcui.gateway.auth.FailureCooldownPolicy;
import com.chatcui.gateway.auth.ReplayGuard;
import com.chatcui.gateway.auth.model.AuthCredentialRecord;
import com.chatcui.gateway.auth.model.AuthRequest;
import com.chatcui.gateway.http.AuthEntryInterceptor;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class ReauthOnReconnectIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");

    @Test
    void reconnectWithoutFreshAuthIsRejectedButFreshSignatureSucceeds() {
        AuthEntryInterceptor interceptor = buildGatewayInterceptor("ACTIVE");
        AuthConfigLoader loader = new AuthConfigLoader();
        AuthConfigLoader.LoadedAuthConfig config = loader.load(baseConfig("active"));
        WindowsKeystoreCredentialProvider provider = new WindowsKeystoreCredentialProvider(new HashMap<>());
        provider.upsertSecret(config.secretRef(), "secret-a");

        AuthRequest first = signedRequest(config, provider.readSecret(config.secretRef()), "nonce-1", "session-1");
        AuthEntryInterceptor.EntryDecision connected = interceptor.preHandle(first);
        assertTrue(connected.allowed());

        AuthEntryInterceptor.EntryDecision reconnectWithoutFresh = interceptor.preHandle(first);
        assertFalse(reconnectWithoutFresh.allowed());
        assertEquals("AUTH_V1_REPLAY_DETECTED", reconnectWithoutFresh.errorResponse().error_code());

        AuthRequest reconnectFresh = signedRequest(config, provider.readSecret(config.secretRef()), "nonce-2", "session-2");
        AuthEntryInterceptor.EntryDecision reconnectSuccess = interceptor.preHandle(reconnectFresh);
        assertTrue(reconnectSuccess.allowed());
    }

    @Test
    void disabledCredentialBlockedDuringReconnect() {
        AuthEntryInterceptor interceptor = buildGatewayInterceptor("DISABLED");
        AuthConfigLoader loader = new AuthConfigLoader();

        assertEquals(
                "Credential state DISABLED blocks startup",
                org.junit.jupiter.api.Assertions.assertThrows(
                                AuthConfigLoader.AuthConfigException.class,
                                () -> loader.load(baseConfig("disabled")))
                        .getMessage());

        AuthRequest request = signedRequest(
                new AuthConfigLoader.LoadedAuthConfig("tenant-a", "client-a", "ak_live_1234", "secret-ref", null, ""),
                "secret-a",
                "nonce-3",
                "session-3");
        AuthEntryInterceptor.EntryDecision decision = interceptor.preHandle(request);
        assertFalse(decision.allowed());
        assertEquals("AUTH_V1_CREDENTIAL_DISABLED", decision.errorResponse().error_code());
    }

    private AuthEntryInterceptor buildGatewayInterceptor(String state) {
        Map<String, AuthCredentialRecord> credentials = Map.of(
                "tenant-a|client-a|ak_live_1234",
                new AuthCredentialRecord(
                        "tenant-a",
                        "client-a",
                        "ak_live_1234",
                        "secret-a",
                        state,
                        true));
        AuthService.CredentialStore store =
                (tenantId, clientId, ak) -> Optional.ofNullable(credentials.get(tenantId + "|" + clientId + "|" + ak));
        AuthService service = new AuthService(
                store,
                new ReplayGuard(),
                new FailureCooldownPolicy(Duration.ZERO, Duration.ZERO),
                new AuthService.Policy(Duration.ofMinutes(15), Duration.ofMinutes(5)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new AuthEntryInterceptor(service, new ErrorResponseFactory());
    }

    private Map<String, String> baseConfig(String state) {
        Map<String, String> config = new HashMap<>();
        config.put("tenant_id", "tenant-a");
        config.put("client_id", "client-a");
        config.put("ak", "ak_live_1234");
        config.put("secret_ref", "wincred://tenant-a/client-a");
        config.put("state", state);
        return config;
    }

    private AuthRequest signedRequest(
            AuthConfigLoader.LoadedAuthConfig config,
            String secret,
            String nonce,
            String sessionId) {
        AuthRequest draft = new AuthRequest(
                config.ak(),
                config.tenantId(),
                config.clientId(),
                NOW.getEpochSecond(),
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
}
