package com.chatcui.agent.auth;

import java.util.Objects;

public class WindowsKeystoreCredentialProvider implements CredentialProvider {
    private final WindowsCredentialStore credentialStore;

    public WindowsKeystoreCredentialProvider() {
        this(new WindowsCredentialStore());
    }

    public WindowsKeystoreCredentialProvider(WindowsCredentialStore credentialStore) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    }

    @Override
    public String readSecret(String secretRef) {
        return credentialStore.readSecret(secretRef);
    }

    @Override
    public void upsertSecret(String secretRef, String secret) {
        credentialStore.writeSecret(secretRef, secret);
    }

    @Override
    public String toString() {
        return "WindowsKeystoreCredentialProvider{entries=" + credentialStore.entryCount() + "}";
    }
}
