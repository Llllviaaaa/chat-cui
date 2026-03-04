package com.chatcui.gateway.relay;

import com.chatcui.gateway.routing.RouteOwnershipRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles bounded replay for unknown-owner routes.
 */
public class UnknownOwnerRecoveryWorker {
    private final Duration replayWindow;
    private final Clock clock;
    private final RouteOwnershipResolver routeOwnershipResolver;
    private final RetryDispatcher retryDispatcher;

    public UnknownOwnerRecoveryWorker(
            Duration replayWindow,
            Clock clock,
            RouteOwnershipResolver routeOwnershipResolver,
            RetryDispatcher retryDispatcher) {
        this.replayWindow = Objects.requireNonNull(replayWindow, "replayWindow must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.routeOwnershipResolver = Objects.requireNonNull(routeOwnershipResolver, "routeOwnershipResolver must not be null");
        this.retryDispatcher = Objects.requireNonNull(retryDispatcher, "retryDispatcher must not be null");
        if (replayWindow.isZero() || replayWindow.isNegative()) {
            throw new IllegalArgumentException("replayWindow must be positive");
        }
    }

    public RecoveryResult process(RecoveryEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        Instant now = Instant.now(clock);
        if (!now.isBefore(entry.firstSeenAt().plus(replayWindow))) {
            return RecoveryResult.replayWindowExpired(entry.traceId(), entry.routeVersionHint());
        }

        Optional<RouteOwnershipRecord> route = routeOwnershipResolver.load(entry.tenantId(), entry.sessionId());
        if (route.isEmpty()) {
            return RecoveryResult.pendingRecheck(entry.traceId(), entry.routeVersionHint());
        }

        RouteOwnershipRecord resolved = route.get();
        retryDispatcher.dispatch(entry, resolved);
        return RecoveryResult.retried(entry.traceId(), resolved.routeVersion());
    }

    @FunctionalInterface
    public interface RouteOwnershipResolver {
        Optional<RouteOwnershipRecord> load(String tenantId, String sessionId);
    }

    @FunctionalInterface
    public interface RetryDispatcher {
        void dispatch(RecoveryEntry entry, RouteOwnershipRecord routeRecord);
    }

    public enum RecoveryStatus {
        RETRIED,
        PENDING_ROUTE_RECHECK,
        REPLAY_WINDOW_EXPIRED
    }

    public record RecoveryEntry(
            String tenantId,
            String sessionId,
            String turnId,
            long seq,
            String topic,
            String traceId,
            Instant firstSeenAt,
            long routeVersionHint) {
        public RecoveryEntry {
            tenantId = requireValue(tenantId, "tenant_id");
            sessionId = requireValue(sessionId, "session_id");
            turnId = requireValue(turnId, "turn_id");
            topic = requireValue(topic, "topic");
            traceId = requireValue(traceId, "trace_id");
            firstSeenAt = Objects.requireNonNull(firstSeenAt, "first_seen_at must not be null");
            if (routeVersionHint < 0) {
                throw new IllegalArgumentException("route_version_hint must be >= 0");
            }
        }
    }

    public record RecoveryResult(
            RecoveryStatus status,
            String errorCode,
            String nextAction,
            String traceId,
            long routeVersion) {
        public RecoveryResult {
            status = Objects.requireNonNull(status, "status must not be null");
            errorCode = normalizeOptional(errorCode);
            nextAction = normalizeOptional(nextAction);
            traceId = requireValue(traceId, "trace_id");
            if (routeVersion < 0) {
                throw new IllegalArgumentException("route_version must be >= 0");
            }
        }

        public static RecoveryResult retried(String traceId, long routeVersion) {
            return new RecoveryResult(RecoveryStatus.RETRIED, null, null, traceId, routeVersion);
        }

        public static RecoveryResult pendingRecheck(String traceId, long routeVersion) {
            return new RecoveryResult(RecoveryStatus.PENDING_ROUTE_RECHECK, null, "retry_via_route_recheck", traceId, routeVersion);
        }

        public static RecoveryResult replayWindowExpired(String traceId, long routeVersion) {
            return new RecoveryResult(
                    RecoveryStatus.REPLAY_WINDOW_EXPIRED,
                    "ROUTE_REPLAY_WINDOW_EXPIRED",
                    "restart_session",
                    traceId,
                    routeVersion);
        }
    }

    private static String requireValue(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
