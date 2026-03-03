package com.chatcui.gateway.auth.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
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
        Map<AuthFailureCode, Set<String>> documentedMappings = loadDocumentedMappings();

        assertEquals(
                allCodes,
                documentedMappings.keySet(),
                "Every AUTH_V1 code must be represented in the documented mapping source");
    }

    @Test
    void documentedMappingsContainRequiredResponseFields() {
        Map<AuthFailureCode, Set<String>> documentedMappings = loadDocumentedMappings();
        documentedMappings.forEach((code, fields) -> assertTrue(
                fields.containsAll(REQUIRED_RESPONSE_FIELDS),
                () -> "Code " + code.name() + " is missing required response fields"));
    }

    private static Map<AuthFailureCode, Set<String>> loadDocumentedMappings() {
        Path repoRoot = Path.of("").toAbsolutePath().getParent();
        AuthErrorContractDocParser.DocumentContract contract = AuthErrorContractDocParser.parse(repoRoot);
        Set<String> fields = contract.requiredFields();
        return contract.codes().stream()
                .map(AuthFailureCode::valueOf)
                .collect(Collectors.toMap(code -> code, code -> fields));
    }
}
