package com.chatcui.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthCredentialStateTest {

    @Test
    void fromValueDefaultsToActiveWhenMissing() {
        assertEquals(AuthCredentialState.ACTIVE, AuthCredentialState.fromValue(null));
    }

    @Test
    void fromValueParsesCaseInsensitive() {
        assertEquals(AuthCredentialState.ROTATING, AuthCredentialState.fromValue("rotating"));
    }

    @Test
    void disabledBlocksConnection() {
        assertFalse(AuthCredentialState.DISABLED.canConnect());
        assertTrue(AuthCredentialState.ACTIVE.canConnect());
    }
}
