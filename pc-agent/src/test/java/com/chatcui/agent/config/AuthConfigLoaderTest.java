package com.chatcui.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthConfigLoaderTest {
    private final AuthConfigLoader loader = new AuthConfigLoader();

    @Test
    void loadValidConfig() {
        AuthConfigLoader.LoadedAuthConfig config = loader.load(baseConfig());
        assertEquals("tenant-a", config.tenantId());
        assertEquals("client-a", config.clientId());
        assertEquals("ak_live_1234", config.ak());
        assertEquals("wincred://tenant-a/client-a", config.secretRef());
        assertEquals(AuthCredentialState.ACTIVE, config.state());
    }

    @Test
    void missingFieldRejected() {
        Map<String, String> config = baseConfig();
        config.remove("tenant_id");
        assertThrows(AuthConfigLoader.AuthConfigException.class, () -> loader.load(config));
    }

    @Test
    void disabledCredentialBlocked() {
        Map<String, String> config = baseConfig();
        config.put("state", "disabled");
        assertThrows(AuthConfigLoader.AuthConfigException.class, () -> loader.load(config));
    }

    @Test
    void rotatingAllowedWithWarning() {
        Map<String, String> config = baseConfig();
        config.put("state", "rotating");
        AuthConfigLoader.LoadedAuthConfig loaded = loader.load(config);
        assertEquals(AuthCredentialState.ROTATING, loaded.state());
        assertTrue(loaded.warning().contains("rotating"));
    }

    @Test
    void sanitizeForLogRedactsSensitiveFields() {
        Map<String, String> raw = baseConfig();
        raw.put("signature", "raw-signature");
        raw.put("sk", "top-secret");
        Map<String, String> sanitized = loader.sanitizeForLog(raw);

        assertEquals("***REDACTED***", sanitized.get("signature"));
        assertEquals("***REDACTED***", sanitized.get("sk"));
        assertTrue(sanitized.values().stream().noneMatch("top-secret"::equals));
    }

    private Map<String, String> baseConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("tenant_id", "tenant-a");
        config.put("client_id", "client-a");
        config.put("ak", "ak_live_1234");
        config.put("secret_ref", "wincred://tenant-a/client-a");
        config.put("state", "active");
        return config;
    }
}
