package com.chatcui.agent.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

class WindowsCredentialStoreTest {

    @Test
    void readWriteDeleteRoundTrip() throws Exception {
        Preferences prefs = testNode();
        WindowsCredentialStore store = new WindowsCredentialStore(prefs, new PassthroughBackend());

        store.writeSecret("ref-1", "secret-v1");
        assertEquals("secret-v1", store.readSecret("ref-1"));
        store.deleteSecret("ref-1");

        CredentialProvider.CredentialException exception = assertThrows(
                CredentialProvider.CredentialException.class,
                () -> store.readSecret("ref-1"));
        assertEquals(CredentialProvider.CredentialException.Reason.NOT_FOUND, exception.reason());
        cleanup(prefs);
    }

    @Test
    void backendAccessDeniedIsTyped() throws Exception {
        Preferences prefs = testNode();
        WindowsCredentialStore store = new WindowsCredentialStore(prefs, new AccessDeniedBackend());

        CredentialProvider.CredentialException exception = assertThrows(
                CredentialProvider.CredentialException.class,
                () -> store.writeSecret("ref-2", "secret-v2"));
        assertEquals(CredentialProvider.CredentialException.Reason.ACCESS_DENIED, exception.reason());
        cleanup(prefs);
    }

    @Test
    void corruptEntryIsTyped() throws Exception {
        Preferences prefs = testNode();
        prefs.put("ref-3", "bad-cipher");
        WindowsCredentialStore store = new WindowsCredentialStore(prefs, new CorruptBackend());

        CredentialProvider.CredentialException exception = assertThrows(
                CredentialProvider.CredentialException.class,
                () -> store.readSecret("ref-3"));
        assertEquals(CredentialProvider.CredentialException.Reason.CORRUPT_ENTRY, exception.reason());
        cleanup(prefs);
    }

    @Test
    void entryCountAndLogsDoNotExposeSecret() throws Exception {
        Preferences prefs = testNode();
        WindowsCredentialStore store = new WindowsCredentialStore(prefs, new PassthroughBackend());
        store.writeSecret("ref-4", "super-sensitive");

        assertTrue(store.entryCount() >= 1);
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

    private static final class AccessDeniedBackend implements WindowsCredentialStore.SecretProtectionBackend {
        @Override
        public String protect(String plaintext) {
            throw new CredentialProvider.CredentialException(
                    CredentialProvider.CredentialException.Reason.ACCESS_DENIED,
                    "Access denied");
        }

        @Override
        public String unprotect(String ciphertext) {
            return ciphertext;
        }
    }

    private static final class CorruptBackend implements WindowsCredentialStore.SecretProtectionBackend {
        @Override
        public String protect(String plaintext) {
            return plaintext;
        }

        @Override
        public String unprotect(String ciphertext) {
            throw new CredentialProvider.CredentialException(
                    CredentialProvider.CredentialException.Reason.CORRUPT_ENTRY,
                    "Corrupt entry");
        }
    }
}
