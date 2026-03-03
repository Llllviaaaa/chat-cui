package com.chatcui.gateway.auth.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AuthErrorContractDocSyncTest {

    @Test
    void docCodeRegistryMatchesEnumExactly() {
        AuthErrorContractDocParser.DocumentContract contract = parseContract();
        Set<String> enumCodes = Arrays.stream(AuthFailureCode.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertEquals(enumCodes, contract.codes(), "AUTH_V1 code registry in docs must match enum set");
    }

    @Test
    void envelopeListsMandatoryDiagnosticFields() {
        AuthErrorContractDocParser.DocumentContract contract = parseContract();
        Set<String> required = contract.requiredFields();

        assertTrue(required.contains("error_code"));
        assertTrue(required.contains("message"));
        assertTrue(required.contains("next_action"));
        assertTrue(required.contains("trace_id"));
        assertTrue(required.contains("session_id"));
        assertTrue(required.contains("debug_id"));
    }

    private static AuthErrorContractDocParser.DocumentContract parseContract() {
        Path repoRoot = Path.of("").toAbsolutePath().getParent();
        return AuthErrorContractDocParser.parse(repoRoot);
    }
}
