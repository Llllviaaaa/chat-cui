package com.chatcui.agent.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Placeholder Windows keystore adapter for phase-1 auth bootstrap.
 * Real OS-keychain bridge can replace this class without changing callers.
 */
public class WindowsKeystoreCredentialProvider implements CredentialProvider {
    private final Map<String, String> keystore;

    public WindowsKeystoreCredentialProvider() {
        this(new ConcurrentHashMap<>());
    }

    public WindowsKeystoreCredentialProvider(Map<String, String> keystore) {
        this.keystore = keystore;
    }

    @Override
    public String readSecret(String secretRef) {
        validateRef(secretRef);
        String secret = keystore.get(secretRef);
        if (secret == null) {
            throw new CredentialException(CredentialException.Reason.NOT_FOUND, "Secret ref not found");
        }
        if (secret.isBlank()) {
            throw new CredentialException(CredentialException.Reason.CORRUPT_ENTRY, "Secret entry is empty");
        }
        return secret;
    }

    @Override
    public void upsertSecret(String secretRef, String secret) {
        validateRef(secretRef);
        if (secret == null || secret.isBlank()) {
            throw new CredentialException(CredentialException.Reason.INVALID_INPUT, "Secret must not be blank");
        }
        keystore.put(secretRef, secret);
    }

    private static void validateRef(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            throw new CredentialException(CredentialException.Reason.INVALID_INPUT, "secretRef must not be blank");
        }
    }

    @Override
    public String toString() {
        return "WindowsKeystoreCredentialProvider{entries=" + keystore.size() + "}";
    }
}
