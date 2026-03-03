package com.chatcui.gateway.auth.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Canonical authenticated principal for gateway auth pipeline.
 */
public record AuthPrincipal(
        String tenantId,
        String clientId,
        String traceId,
        String sessionId,
        String credentialState,
        Instant issuedAt
) {
    public AuthPrincipal {
        tenantId = require(tenantId, "tenantId");
        clientId = require(clientId, "clientId");
        traceId = require(traceId, "traceId");
        sessionId = require(sessionId, "sessionId");
        credentialState = require(credentialState, "credentialState");
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

