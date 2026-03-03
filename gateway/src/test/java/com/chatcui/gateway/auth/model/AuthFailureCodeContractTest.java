package com.chatcui.gateway.auth.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AuthFailureCodeContractTest {

    private static final Set<String> REQUIRED_RESPONSE_FIELDS = Set.of(
            "error_code",
            "message",
            "next_action",
            "trace_id",
            "session_id",
            "debug_id");

    private static final Map<AuthFailureCode, Set<String>> DOCUMENTED_RESPONSE_FIELDS =
            new EnumMap<>(AuthFailureCode.class);

    static {
        DOCUMENTED_RESPONSE_FIELDS.put(AuthFailureCode.AUTH_V1_MISSING_CREDENTIAL, REQUIRED_RESPONSE_FIELDS);
        DOCUMENTED_RESPONSE_FIELDS.put(AuthFailureCode.AUTH_V1_INVALID_SIGNATURE, REQUIRED_RESPONSE_FIELDS);
        DOCUMENTED_RESPONSE_FIELDS.put(AuthFailureCode.AUTH_V1_TIMESTAMP_OUT_OF_WINDOW, REQUIRED_RESPONSE_FIELDS);
        DOCUMENTED_RESPONSE_FIELDS.put(AuthFailureCode.AUTH_V1_REPLAY_DETECTED, REQUIRED_RESPONSE_FIELDS);
        DOCUMENTED_RESPONSE_FIELDS.put(AuthFailureCode.AUTH_V1_COOLDOWN_ACTIVE, REQUIRED_RESPONSE_FIELDS);
        DOCUMENTED_RESPONSE_FIELDS.put(AuthFailureCode.AUTH_V1_CREDENTIAL_DISABLED, REQUIRED_RESPONSE_FIELDS);
        DOCUMENTED_RESPONSE_FIELDS.put(AuthFailureCode.AUTH_V1_PERMISSION_DENIED, REQUIRED_RESPONSE_FIELDS);
    }

    @Test
    void authV1CodesAreUniqueAndPrefixed() {
        AuthFailureCode[] values = AuthFailureCode.values();
        Set<String> codeNames = Arrays.stream(values)
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(values.length, codeNames.size(), "AUTH_V1 codes must be unique");
        assertTrue(
                codeNames.stream().allMatch(code -> code.startsWith("AUTH_V1_")),
                "Every code must be prefixed with AUTH_V1_");
    }

    @Test
    void everyCodeHasDocumentedMapping() {
        Set<AuthFailureCode> allCodes = Set.of(AuthFailureCode.values());

        assertEquals(
                allCodes,
                DOCUMENTED_RESPONSE_FIELDS.keySet(),
                "Every AUTH_V1 code must be represented in the documented mapping source");
    }

    @Test
    void documentedMappingsContainRequiredResponseFields() {
        DOCUMENTED_RESPONSE_FIELDS.forEach((code, fields) -> assertTrue(
                fields.containsAll(REQUIRED_RESPONSE_FIELDS),
                () -> "Code " + code.name() + " is missing required response fields"));
    }
}
