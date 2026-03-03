package com.chatcui.gateway.auth.model;

public record AuthRequest(
        String ak,
        String tenantId,
        String clientId,
        long timestamp,
        String nonce,
        String sessionId,
        String signature,
        String traceId
) {
    public boolean hasRequiredFields() {
        return notBlank(ak)
                && notBlank(tenantId)
                && notBlank(clientId)
                && timestamp > 0
                && notBlank(nonce)
                && notBlank(sessionId)
                && notBlank(signature);
    }

    public String principalKey() {
        return tenantId + "|" + clientId + "|" + ak;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
