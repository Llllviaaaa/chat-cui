package com.chatcui.gateway.auth.model;

public record AuthCredentialRecord(
        String tenantId,
        String clientId,
        String ak,
        String secret,
        String state,
        boolean permitted
) {
    public boolean isDisabled() {
        return "DISABLED".equalsIgnoreCase(state);
    }

    public String normalizedState() {
        return state == null ? "ACTIVE" : state.toUpperCase();
    }
}
