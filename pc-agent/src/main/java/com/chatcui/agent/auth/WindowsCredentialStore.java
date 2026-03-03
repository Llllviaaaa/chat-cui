package com.chatcui.agent.auth;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Windows-backed credential store facade.
 *
 * Secrets are protected via DPAPI in the current user scope before persistence.
 */
public class WindowsCredentialStore {
    private static final String PREF_NODE = "com/chatcui/agent/credentials";
    private final Preferences preferences;
    private final SecretProtectionBackend backend;

    public WindowsCredentialStore() {
        this(Preferences.userRoot().node(PREF_NODE), new DpapiSecretProtectionBackend());
    }

    public WindowsCredentialStore(Preferences preferences, SecretProtectionBackend backend) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public String readSecret(String secretRef) {
        validateRef(secretRef);
        String encrypted = preferences.get(secretRef, null);
        if (encrypted == null) {
            throw new CredentialProvider.CredentialException(
                    CredentialProvider.CredentialException.Reason.NOT_FOUND,
                    "Secret ref not found");
        }
        return backend.unprotect(encrypted);
    }

    public void writeSecret(String secretRef, String secret) {
        validateRef(secretRef);
        if (secret == null || secret.isBlank()) {
            throw new CredentialProvider.CredentialException(
                    CredentialProvider.CredentialException.Reason.INVALID_INPUT,
                    "Secret must not be blank");
        }
        String encrypted = backend.protect(secret);
        preferences.put(secretRef, encrypted);
        flushOrAccessDenied();
    }

    public void deleteSecret(String secretRef) {
        validateRef(secretRef);
        preferences.remove(secretRef);
        flushOrAccessDenied();
    }

    int entryCount() {
        try {
            return preferences.keys().length;
        } catch (BackingStoreException e) {
            return -1;
        }
    }

    private void flushOrAccessDenied() {
        try {
            preferences.flush();
        } catch (BackingStoreException e) {
            throw new CredentialProvider.CredentialException(
                    CredentialProvider.CredentialException.Reason.ACCESS_DENIED,
                    "Unable to persist credential store");
        }
    }

    private static void validateRef(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            throw new CredentialProvider.CredentialException(
                    CredentialProvider.CredentialException.Reason.INVALID_INPUT,
                    "secretRef must not be blank");
        }
    }

    public interface SecretProtectionBackend {
        String protect(String plaintext);

        String unprotect(String ciphertext);
    }

    static final class DpapiSecretProtectionBackend implements SecretProtectionBackend {
        @Override
        public String protect(String plaintext) {
            return runPowerShell(plaintext, true);
        }

        @Override
        public String unprotect(String ciphertext) {
            return runPowerShell(ciphertext, false);
        }

        private String runPowerShell(String input, boolean protect) {
            String variable = protect ? "CHATCUI_DPAPI_PLAINTEXT" : "CHATCUI_DPAPI_CIPHERTEXT";
            String script = protect
                    ? "$raw=$env:CHATCUI_DPAPI_PLAINTEXT;"
                    + "$bytes=[System.Text.Encoding]::UTF8.GetBytes($raw);"
                    + "$enc=[System.Security.Cryptography.ProtectedData]::Protect($bytes,$null,[System.Security.Cryptography.DataProtectionScope]::CurrentUser);"
                    + "[Convert]::ToBase64String($enc)"
                    : "$raw=$env:CHATCUI_DPAPI_CIPHERTEXT;"
                    + "$enc=[Convert]::FromBase64String($raw);"
                    + "$bytes=[System.Security.Cryptography.ProtectedData]::Unprotect($enc,$null,[System.Security.Cryptography.DataProtectionScope]::CurrentUser);"
                    + "[System.Text.Encoding]::UTF8.GetString($bytes)";
            ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-Command", script);
            builder.environment().put(variable, input);
            try {
                Process process = builder.start();
                String stdout = readFully(process.getInputStream()).trim();
                String stderr = readFully(process.getErrorStream()).trim();
                int code = process.waitFor();
                if (code != 0) {
                    CredentialProvider.CredentialException.Reason reason = protect
                            ? CredentialProvider.CredentialException.Reason.ACCESS_DENIED
                            : CredentialProvider.CredentialException.Reason.CORRUPT_ENTRY;
                    throw new CredentialProvider.CredentialException(reason, safeError(stderr));
                }
                if (stdout.isBlank()) {
                    CredentialProvider.CredentialException.Reason reason = protect
                            ? CredentialProvider.CredentialException.Reason.ACCESS_DENIED
                            : CredentialProvider.CredentialException.Reason.CORRUPT_ENTRY;
                    throw new CredentialProvider.CredentialException(reason, "Windows DPAPI returned empty output");
                }
                return stdout;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CredentialProvider.CredentialException(
                        CredentialProvider.CredentialException.Reason.ACCESS_DENIED,
                        "Interrupted while accessing Windows DPAPI");
            } catch (CredentialProvider.CredentialException e) {
                throw e;
            } catch (Exception e) {
                throw new CredentialProvider.CredentialException(
                        CredentialProvider.CredentialException.Reason.ACCESS_DENIED,
                        "Windows DPAPI invocation failed");
            } finally {
                builder.environment().put(variable, UUID.randomUUID().toString());
            }
        }

        private static String readFully(InputStream stream) throws Exception {
            try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
                return output.toString(StandardCharsets.UTF_8);
            }
        }

        private static String safeError(String stderr) {
            if (stderr == null || stderr.isBlank()) {
                return "Windows DPAPI command failed";
            }
            // Keep logs/error payloads secret-safe.
            return stderr.length() > 160 ? stderr.substring(0, 160) : stderr;
        }
    }
}
