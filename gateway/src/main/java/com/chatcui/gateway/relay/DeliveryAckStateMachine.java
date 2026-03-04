package com.chatcui.gateway.relay;

import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks deterministic delivery stages per relay tuple.
 */
public class DeliveryAckStateMachine {
    private final Map<String, AckSnapshot> statesByTuple = new ConcurrentHashMap<>();

    public AckSnapshot markGatewayOwnerAccepted(SkillTurnForwardEvent event, long routeVersion) {
        Objects.requireNonNull(event, "event must not be null");
        requireRouteVersion(routeVersion);
        String tuple = tupleKey(event);
        return statesByTuple.compute(tuple, (key, existing) -> {
            if (existing == null) {
                return new AckSnapshot(
                        Stage.GATEWAY_OWNER_ACCEPTED,
                        Stage.GATEWAY_OWNER_ACCEPTED.value(),
                        null,
                        null,
                        traceIdOrUnknown(event.traceId()),
                        routeVersion,
                        tuple);
            }
            return existing;
        });
    }

    public AckSnapshot markClientDelivered(SkillTurnForwardEvent event, long routeVersion) {
        Objects.requireNonNull(event, "event must not be null");
        requireRouteVersion(routeVersion);
        String tuple = tupleKey(event);
        return statesByTuple.compute(tuple, (key, existing) -> {
            if (existing == null) {
                return new AckSnapshot(
                        Stage.CLIENT_DELIVERED,
                        Stage.CLIENT_DELIVERED.value(),
                        null,
                        null,
                        traceIdOrUnknown(event.traceId()),
                        routeVersion,
                        tuple);
            }
            if (existing.stage() == Stage.CLIENT_DELIVERY_TIMEOUT) {
                return existing;
            }
            return new AckSnapshot(
                    Stage.CLIENT_DELIVERED,
                    Stage.CLIENT_DELIVERED.value(),
                    null,
                    null,
                    existing.traceId(),
                    routeVersion,
                    tuple);
        });
    }

    public AckSnapshot markClientDeliveryTimeout(
            SkillTurnForwardEvent event,
            long routeVersion,
            String errorCode,
            String nextAction) {
        Objects.requireNonNull(event, "event must not be null");
        requireRouteVersion(routeVersion);
        String normalizedErrorCode = requireValue(errorCode, "error_code");
        String normalizedNextAction = requireValue(nextAction, "next_action");
        String tuple = tupleKey(event);
        return statesByTuple.compute(tuple, (key, existing) -> {
            if (existing != null && existing.stage() == Stage.CLIENT_DELIVERED) {
                return existing;
            }
            String traceId = existing == null ? traceIdOrUnknown(event.traceId()) : existing.traceId();
            return new AckSnapshot(
                    Stage.CLIENT_DELIVERY_TIMEOUT,
                    Stage.CLIENT_DELIVERY_TIMEOUT.value(),
                    normalizedErrorCode,
                    normalizedNextAction,
                    traceId,
                    routeVersion,
                    tuple);
        });
    }

    public Optional<AckSnapshot> current(SkillTurnForwardEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return Optional.ofNullable(statesByTuple.get(tupleKey(event)));
    }

    private static void requireRouteVersion(long routeVersion) {
        if (routeVersion < 0) {
            throw new IllegalArgumentException("route_version must be >= 0");
        }
    }

    private static String tupleKey(SkillTurnForwardEvent event) {
        return requireValue(event.sessionId(), "session_id")
                + "|"
                + requireValue(event.turnId(), "turn_id")
                + "|"
                + event.seq()
                + "|"
                + requireValue(event.topic(), "topic");
    }

    private static String requireValue(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String traceIdOrUnknown(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return "trace-unknown";
        }
        return traceId;
    }

    public enum Stage {
        GATEWAY_OWNER_ACCEPTED("gateway_owner_accepted"),
        CLIENT_DELIVERED("client_delivered"),
        CLIENT_DELIVERY_TIMEOUT("client_delivery_timeout");

        private final String value;

        Stage(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public record AckSnapshot(
            Stage stage,
            String stageValue,
            String errorCode,
            String nextAction,
            String traceId,
            long routeVersion,
            String deliveryTuple) {
        public AckSnapshot {
            stage = Objects.requireNonNull(stage, "stage must not be null");
            stageValue = requireValue(stageValue, "stage_value");
            errorCode = normalizeOptional(errorCode);
            nextAction = normalizeOptional(nextAction);
            traceId = requireValue(traceId, "trace_id");
            if (routeVersion < 0) {
                throw new IllegalArgumentException("route_version must be >= 0");
            }
            deliveryTuple = requireValue(deliveryTuple, "delivery_tuple");
        }

        private static String normalizeOptional(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim();
            return normalized.isEmpty() ? null : normalized;
        }
    }
}
