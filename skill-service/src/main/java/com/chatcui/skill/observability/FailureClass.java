package com.chatcui.skill.observability;

import java.util.Arrays;
import java.util.Locale;

public enum FailureClass {
    AUTH("auth", false),
    BRIDGE("bridge", true),
    PERSISTENCE("persistence", true),
    SENDBACK("sendback", true),
    UNKNOWN("unknown", false);

    private final String value;
    private final boolean retryableDefault;

    FailureClass(String value, boolean retryableDefault) {
        this.value = value;
        this.retryableDefault = retryableDefault;
    }

    public String value() {
        return value;
    }

    public boolean retryableDefault() {
        return retryableDefault;
    }

    public static FailureClass fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equals(normalized))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
