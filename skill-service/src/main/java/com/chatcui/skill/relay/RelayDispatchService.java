package com.chatcui.skill.relay;

import java.util.Objects;
import java.util.Optional;

public class RelayDispatchService {
    private final RouteResolver routeResolver;
    private final GatewayDispatchGateway gatewayDispatchGateway;

    public RelayDispatchService(RouteResolver routeResolver, GatewayDispatchGateway gatewayDispatchGateway) {
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.gatewayDispatchGateway = Objects.requireNonNull(gatewayDispatchGateway, "gatewayDispatchGateway must not be null");
    }

    public DispatchOutcome dispatch(RelayEvent event, String localSkillOwner) {
        Objects.requireNonNull(event, "event must not be null");
        String normalizedLocalSkillOwner = requireValue(localSkillOwner, "local_skill_owner");

        Optional<RouteSnapshot> routeSnapshot = routeResolver.load(event.tenantId(), event.sessionId());
        if (routeSnapshot.isEmpty()) {
            return DispatchOutcome.pendingRetry("route_missing", null);
        }

        RouteSnapshot route = routeSnapshot.get();
        if (!normalizedLocalSkillOwner.equals(route.skillOwner())) {
            return DispatchOutcome.skippedNotOwner(route.gatewayOwner());
        }

        try {
            gatewayDispatchGateway.forward(route.gatewayOwner(), event);
            return DispatchOutcome.dispatched(route.gatewayOwner());
        } catch (RuntimeException ex) {
            return DispatchOutcome.pendingRetry("dispatch_failed", route.gatewayOwner());
        }
    }

    private static String requireValue(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    @FunctionalInterface
    public interface RouteResolver {
        Optional<RouteSnapshot> load(String tenantId, String sessionId);
    }

    @FunctionalInterface
    public interface GatewayDispatchGateway {
        void forward(String targetGatewayOwner, RelayEvent event);
    }

    public record RouteSnapshot(
            String tenantId,
            String sessionId,
            long routeVersion,
            String skillOwner,
            String gatewayOwner) {
        public RouteSnapshot {
            tenantId = requireValue(tenantId, "tenant_id");
            sessionId = requireValue(sessionId, "session_id");
            if (routeVersion < 0) {
                throw new IllegalArgumentException("route_version must be >= 0");
            }
            skillOwner = requireValue(skillOwner, "skill_owner");
            gatewayOwner = requireValue(gatewayOwner, "gateway_owner");
        }
    }

    public record RelayEvent(
            String tenantId,
            String clientId,
            String sessionId,
            String turnId,
            long seq,
            String topic,
            String traceId,
            long routeVersion,
            String sourceGatewayOwner,
            String targetSkillOwner,
            String targetGatewayOwner,
            String hop,
            String partitionKey,
            String dedupeKey,
            String actor,
            String eventType,
            String payload,
            String reasonCode,
            String nextAction) {
        public RelayEvent {
            tenantId = requireValue(tenantId, "tenant_id");
            clientId = requireValue(clientId, "client_id");
            sessionId = requireValue(sessionId, "session_id");
            turnId = requireValue(turnId, "turn_id");
            topic = requireValue(topic, "topic");
            traceId = requireValue(traceId, "trace_id");
            if (routeVersion < 0) {
                throw new IllegalArgumentException("route_version must be >= 0");
            }
            sourceGatewayOwner = requireValue(sourceGatewayOwner, "source_gateway_owner");
            targetSkillOwner = requireValue(targetSkillOwner, "target_skill_owner");
            targetGatewayOwner = requireValue(targetGatewayOwner, "target_gateway_owner");
            hop = requireValue(hop, "hop");
            partitionKey = requireValue(partitionKey, "partition_key");
            dedupeKey = requireValue(dedupeKey, "dedupe_key");
            actor = requireValue(actor, "actor");
            eventType = requireValue(eventType, "event_type");
            payload = payload == null ? "" : payload;
        }

        public static String dedupeTuple(String sessionId, String turnId, long seq, String topic) {
            return requireValue(sessionId, "session_id")
                    + "|"
                    + requireValue(turnId, "turn_id")
                    + "|"
                    + seq
                    + "|"
                    + requireValue(topic, "topic");
        }
    }

    public enum DispatchStatus {
        DISPATCHED,
        SKIPPED_NOT_OWNER,
        PENDING_RETRY
    }

    public record DispatchOutcome(DispatchStatus status, String reason, String targetGatewayOwner) {
        static DispatchOutcome dispatched(String targetGatewayOwner) {
            return new DispatchOutcome(DispatchStatus.DISPATCHED, "dispatched", targetGatewayOwner);
        }

        static DispatchOutcome skippedNotOwner(String targetGatewayOwner) {
            return new DispatchOutcome(DispatchStatus.SKIPPED_NOT_OWNER, "not_route_owner", targetGatewayOwner);
        }

        static DispatchOutcome pendingRetry(String reason, String targetGatewayOwner) {
            return new DispatchOutcome(DispatchStatus.PENDING_RETRY, reason, targetGatewayOwner);
        }
    }
}
