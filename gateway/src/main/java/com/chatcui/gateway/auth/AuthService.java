package com.chatcui.gateway.auth;

import com.chatcui.gateway.auth.model.AuthCredentialRecord;
import com.chatcui.gateway.auth.model.AuthFailureCode;
import com.chatcui.gateway.auth.model.AuthPrincipal;
import com.chatcui.gateway.auth.model.AuthRequest;
import com.chatcui.gateway.auth.model.AuthResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.OptionalInt;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class AuthService {
    private final CredentialStore credentialStore;
    private final ReplayGuard replayGuard;
    private final FailureCooldownPolicy cooldownPolicy;
    private final Policy policy;
    private final Clock clock;

    public AuthService(
            CredentialStore credentialStore,
            ReplayGuard replayGuard,
            FailureCooldownPolicy cooldownPolicy,
            Policy policy,
            Clock clock) {
        this.credentialStore = credentialStore;
        this.replayGuard = replayGuard;
        this.cooldownPolicy = cooldownPolicy;
        this.policy = policy;
        this.clock = clock;
    }

    public AuthResult authenticate(AuthRequest request) {
        Instant now = clock.instant();
        if (request == null || !request.hasRequiredFields()) {
            return AuthResult.failure(AuthFailureCode.AUTH_V1_MISSING_CREDENTIAL, null);
        }

        String principalKey = request.principalKey();
        OptionalInt retryAfter = cooldownPolicy.retryAfterSeconds(principalKey, now);
        if (retryAfter.isPresent()) {
            return AuthResult.failure(AuthFailureCode.AUTH_V1_COOLDOWN_ACTIVE, retryAfter.getAsInt());
        }

        Optional<AuthCredentialRecord> optionalRecord =
                credentialStore.find(request.tenantId(), request.clientId(), request.ak());
        if (optionalRecord.isEmpty()) {
            cooldownPolicy.recordFailure(principalKey, now);
            return AuthResult.failure(AuthFailureCode.AUTH_V1_MISSING_CREDENTIAL, null);
        }

        AuthCredentialRecord record = optionalRecord.get();
        if (record.isDisabled()) {
            cooldownPolicy.recordFailure(principalKey, now);
            return AuthResult.failure(AuthFailureCode.AUTH_V1_CREDENTIAL_DISABLED, null);
        }
        if (!record.permitted()) {
            cooldownPolicy.recordFailure(principalKey, now);
            return AuthResult.failure(AuthFailureCode.AUTH_V1_PERMISSION_DENIED, null);
        }

        if (!signatureMatches(request, record.secret())) {
            cooldownPolicy.recordFailure(principalKey, now);
            return AuthResult.failure(AuthFailureCode.AUTH_V1_INVALID_SIGNATURE, null);
        }

        long skew = Math.abs(now.getEpochSecond() - request.timestamp());
        if (skew > policy.skewTolerance().toSeconds()) {
            cooldownPolicy.recordFailure(principalKey, now);
            return AuthResult.failure(AuthFailureCode.AUTH_V1_TIMESTAMP_OUT_OF_WINDOW, null);
        }

        boolean firstSeen = replayGuard.registerIfFirst(replayKey(request), policy.replayTtl(), now);
        if (!firstSeen) {
            cooldownPolicy.recordFailure(principalKey, now);
            return AuthResult.failure(AuthFailureCode.AUTH_V1_REPLAY_DETECTED, null);
        }

        cooldownPolicy.clear(principalKey);
        AuthPrincipal principal = new AuthPrincipal(
                request.tenantId(),
                request.clientId(),
                request.traceId(),
                request.sessionId(),
                record.normalizedState(),
                now);
        return AuthResult.success(principal);
    }

    public static String canonicalPayload(AuthRequest request) {
        return "ak:" + request.ak() + "\n"
                + "tenant_id:" + request.tenantId() + "\n"
                + "client_id:" + request.clientId() + "\n"
                + "timestamp:" + request.timestamp() + "\n"
                + "nonce:" + request.nonce() + "\n"
                + "session_id:" + request.sessionId();
    }

    public static String replayKey(AuthRequest request) {
        return "auth:replay:v1:" + request.tenantId()
                + ":" + request.clientId()
                + ":" + request.ak()
                + ":" + request.nonce();
    }

    private boolean signatureMatches(AuthRequest request, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(canonicalPayload(request).getBytes(StandardCharsets.UTF_8));
            byte[] provided = Base64.getDecoder().decode(request.signature());
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            return false;
        }
    }

    public interface CredentialStore {
        Optional<AuthCredentialRecord> find(String tenantId, String clientId, String ak);
    }

    public record Policy(Duration skewTolerance, Duration replayTtl) {
    }
}
