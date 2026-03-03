package com.chatcui.agent.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

class WindowsKeystoreCredentialProviderTest {

    @Test
    void readsAndUpdatesSecretByReference() throws Exception {
        Preferences prefs = testNode();
        WindowsCredentialStore store = new WindowsCredentialStore(prefs, new PassthroughBackend());
        WindowsKeystoreCredentialProvider provider = new WindowsKeystoreCredentialProvider(store);
        provider.upsertSecret("ref-1", "secret-v1");

        assertEquals("secret-v1", provider.readSecret("ref-1"));
        provider.upsertSecret("ref-1", "secret-v2");
        assertEquals("secret-v2", provider.readSecret("ref-1"));
        cleanup(prefs);
    }

    @Test
    void missingSecretReturnsTypedError() throws Exception {
        Preferences prefs = testNode();
        WindowsCredentialStore store = new WindowsCredentialStore(prefs, new PassthroughBackend());
        WindowsKeystoreCredentialProvider provider = new WindowsKeystoreCredentialProvider(store);
        CredentialProvider.CredentialException exception = assertThrows(
                CredentialProvider.CredentialException.class,
                () -> provider.readSecret("missing"));
        assertEquals(CredentialProvider.CredentialException.Reason.NOT_FOUND, exception.reason());
        cleanup(prefs);
    }

    @Test
    void toStringDoesNotExposeSecretMaterial() throws Exception {
        Preferences prefs = testNode();
        WindowsCredentialStore store = new WindowsCredentialStore(prefs, new PassthroughBackend());
        WindowsKeystoreCredentialProvider provider = new WindowsKeystoreCredentialProvider(store);
        provider.upsertSecret("ref-1", "super-sensitive");
        assertTrue(provider.toString().contains("entries=1"));
        assertTrue(!provider.toString().contains("super-sensitive"));
        cleanup(prefs);
    }

    private static Preferences testNode() {
        return Preferences.userRoot().node("chatcui-tests/" + UUID.randomUUID());
    }

    private static void cleanup(Preferences prefs) throws Exception {
        prefs.removeNode();
        prefs.flush();
    }

    private static final class PassthroughBackend implements WindowsCredentialStore.SecretProtectionBackend {
        @Override
        public String protect(String plaintext) {
            return "enc:" + plaintext;
        }

        @Override
        public String unprotect(String ciphertext) {
            if (!ciphertext.startsWith("enc:")) {
                throw new CredentialProvider.CredentialException(
                        CredentialProvider.CredentialException.Reason.CORRUPT_ENTRY,
                        "Corrupt entry");
            }
            return ciphertext.substring(4);
        }
    }
}
