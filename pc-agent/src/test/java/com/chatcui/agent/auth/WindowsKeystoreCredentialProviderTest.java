package com.chatcui.agent.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WindowsKeystoreCredentialProviderTest {

    @Test
    void readsAndUpdatesSecretByReference() {
        Map<String, String> store = new HashMap<>();
        WindowsKeystoreCredentialProvider provider = new WindowsKeystoreCredentialProvider(store);
        provider.upsertSecret("ref-1", "secret-v1");

        assertEquals("secret-v1", provider.readSecret("ref-1"));
        provider.upsertSecret("ref-1", "secret-v2");
        assertEquals("secret-v2", provider.readSecret("ref-1"));
    }

    @Test
    void missingSecretReturnsTypedError() {
        WindowsKeystoreCredentialProvider provider = new WindowsKeystoreCredentialProvider();
        CredentialProvider.CredentialException exception = assertThrows(
                CredentialProvider.CredentialException.class,
                () -> provider.readSecret("missing"));
        assertEquals(CredentialProvider.CredentialException.Reason.NOT_FOUND, exception.reason());
    }

    @Test
    void toStringDoesNotExposeSecretMaterial() {
        Map<String, String> store = new HashMap<>();
        WindowsKeystoreCredentialProvider provider = new WindowsKeystoreCredentialProvider(store);
        provider.upsertSecret("ref-1", "super-sensitive");
        assertTrue(provider.toString().contains("entries=1"));
        assertTrue(!provider.toString().contains("super-sensitive"));
    }
}
