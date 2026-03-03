package com.chatcui.gateway.auth;

import com.chatcui.gateway.auth.model.AuthErrorResponse;
import com.chatcui.gateway.auth.model.AuthFailureCode;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class ErrorResponseFactory {
    private final Map<AuthFailureCode, Mapping> mappings = new EnumMap<>(AuthFailureCode.class);

    public ErrorResponseFactory() {
        mappings.put(AuthFailureCode.AUTH_V1_MISSING_CREDENTIAL, new Mapping(400, 4400,
                "Required authentication metadata is missing.",
                "Provide AK/signature/timestamp/nonce/session_id and retry.",
                false));
        mappings.put(AuthFailureCode.AUTH_V1_INVALID_SIGNATURE, new Mapping(401, 4401,
                "Authentication failed.",
                "Verify canonical signing fields and SK, then retry.",
                false));
        mappings.put(AuthFailureCode.AUTH_V1_TIMESTAMP_OUT_OF_WINDOW, new Mapping(401, 4401,
                "Request timestamp is outside allowed window.",
                "Sync client clock and retry with a fresh signature.",
                false));
        mappings.put(AuthFailureCode.AUTH_V1_REPLAY_DETECTED, new Mapping(401, 4401,
                "Replay request detected.",
                "Generate a new nonce and signature, then retry once.",
                false));
        mappings.put(AuthFailureCode.AUTH_V1_COOLDOWN_ACTIVE, new Mapping(429, 4429,
                "Too many failed authentication attempts.",
                "Wait before retrying authentication.",
                true));
        mappings.put(AuthFailureCode.AUTH_V1_CREDENTIAL_DISABLED, new Mapping(403, 4403,
                "Credential is disabled.",
                "Contact tenant admin to re-enable or rotate credential.",
                false));
        mappings.put(AuthFailureCode.AUTH_V1_PERMISSION_DENIED, new Mapping(403, 4403,
                "Access denied for this client.",
                "Request required permission for tenant/client binding.",
                false));
    }

    public int httpStatus(AuthFailureCode code) {
        return mappingFor(code).httpStatus;
    }

    public int wsCloseCode(AuthFailureCode code) {
        return mappingFor(code).wsCloseCode;
    }

    public AuthErrorResponse toResponse(
            AuthFailureCode code,
            String traceId,
            String sessionId,
            Integer retryAfterSeconds) {
        Mapping mapping = mappingFor(code);
        Integer retryAfter = mapping.includeRetryAfter ? retryAfterSeconds : null;
        return new AuthErrorResponse(
                code.name(),
                mapping.message,
                mapping.nextAction,
                retryAfter,
                emptyIfNull(traceId),
                emptyIfNull(sessionId),
                UUID.randomUUID().toString());
    }

    private Mapping mappingFor(AuthFailureCode code) {
        Mapping mapping = mappings.get(code);
        if (mapping == null) {
            throw new IllegalArgumentException("No mapping for code: " + code);
        }
        return mapping;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private record Mapping(
            int httpStatus,
            int wsCloseCode,
            String message,
            String nextAction,
            boolean includeRetryAfter) {
    }
}
