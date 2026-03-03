package com.chatcui.agent.config;

import java.util.Locale;

public enum AuthCredentialState {
    ACTIVE,
    DISABLED,
    ROTATING;

    public static AuthCredentialState fromValue(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        return AuthCredentialState.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public boolean canConnect() {
        return this != DISABLED;
    }
}
