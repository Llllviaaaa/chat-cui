package com.chatcui.gateway.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.auth.model.AuthErrorResponse;
import com.chatcui.gateway.auth.model.AuthFailureCode;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class ErrorResponseFactoryTest {
    private final ErrorResponseFactory factory = new ErrorResponseFactory();

    @Test
    void everyFailureCodeProducesRequiredFields() {
        for (AuthFailureCode code : EnumSet.allOf(AuthFailureCode.class)) {
            AuthErrorResponse response = factory.toResponse(code, "trace", "session", 11);
            assertEquals(code.name(), response.error_code());
            assertNotNull(response.message());
            assertNotNull(response.next_action());
            assertNotNull(response.trace_id());
            assertNotNull(response.session_id());
            assertNotNull(response.debug_id());
            assertTrue(response.debug_id().length() > 10);
        }
    }

    @Test
    void cooldownCodeIncludesRetryAfterOnlyForCooldown() {
        AuthErrorResponse cooldown = factory.toResponse(AuthFailureCode.AUTH_V1_COOLDOWN_ACTIVE, "t", "s", 9);
        AuthErrorResponse signature = factory.toResponse(AuthFailureCode.AUTH_V1_INVALID_SIGNATURE, "t", "s", 9);

        assertEquals(9, cooldown.retry_after());
        assertNull(signature.retry_after());
    }

    @Test
    void wsAndHttpMappingsAreDeterministic() {
        assertEquals(429, factory.httpStatus(AuthFailureCode.AUTH_V1_COOLDOWN_ACTIVE));
        assertEquals(4429, factory.wsCloseCode(AuthFailureCode.AUTH_V1_COOLDOWN_ACTIVE));
        assertEquals(403, factory.httpStatus(AuthFailureCode.AUTH_V1_PERMISSION_DENIED));
        assertEquals(4403, factory.wsCloseCode(AuthFailureCode.AUTH_V1_PERMISSION_DENIED));
    }
}
