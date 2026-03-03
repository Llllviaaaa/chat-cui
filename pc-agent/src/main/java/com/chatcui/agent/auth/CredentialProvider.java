package com.chatcui.agent.auth;

public interface CredentialProvider {
    String readSecret(String secretRef);

    void upsertSecret(String secretRef, String secret);

    final class CredentialException extends RuntimeException {
        public enum Reason {
            NOT_FOUND,
            ACCESS_DENIED,
            CORRUPT_ENTRY,
            INVALID_INPUT
        }

        private final Reason reason;

        public CredentialException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }
}
