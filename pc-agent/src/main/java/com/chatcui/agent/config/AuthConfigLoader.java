package com.chatcui.agent.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class AuthConfigLoader {
    public LoadedAuthConfig load(Map<String, String> rawConfig) {
        Objects.requireNonNull(rawConfig, "rawConfig");
        String tenantId = require(rawConfig, "tenant_id");
        String clientId = require(rawConfig, "client_id");
        String ak = require(rawConfig, "ak");
        String secretRef = require(rawConfig, "secret_ref");
        AuthCredentialState state = AuthCredentialState.fromValue(rawConfig.get("state"));

        if (state == AuthCredentialState.DISABLED) {
            throw new AuthConfigException("Credential state DISABLED blocks startup");
        }

        String warning = state == AuthCredentialState.ROTATING
                ? "Credential is rotating. Continue with warning in phase 1."
                : "";
        return new LoadedAuthConfig(tenantId, clientId, ak, secretRef, state, warning);
    }

    public Map<String, String> sanitizeForLog(Map<String, String> config) {
        Map<String, String> safe = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null) {
                continue;
            }
            String normalizedKey = key.toLowerCase();
            if (normalizedKey.contains("secret") || normalizedKey.contains("signature") || normalizedKey.equals("sk")) {
                safe.put(key, "***REDACTED***");
            } else {
                safe.put(key, value);
            }
        }
        return safe;
    }

    private String require(Map<String, String> raw, String key) {
        String value = raw.get(key);
        if (value == null || value.isBlank()) {
            throw new AuthConfigException("Missing required field: " + key);
        }
        return value.trim();
    }

    public record LoadedAuthConfig(
            String tenantId,
            String clientId,
            String ak,
            String secretRef,
            AuthCredentialState state,
            String warning) {
    }

    public static final class AuthConfigException extends RuntimeException {
        public AuthConfigException(String message) {
            super(message);
        }
    }
}
