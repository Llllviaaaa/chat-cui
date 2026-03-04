package com.chatcui.gateway.relay;

import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import com.chatcui.gateway.routing.RouteOwnershipRecord;
import java.util.Objects;

public record RelayEnvelope(
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
    public static final String HOP_GATEWAY_TO_SKILL_OWNER = "gateway_to_skill_owner";

    public RelayEnvelope {
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

    public static RelayEnvelope firstHop(
            SkillTurnForwardEvent event,
            RouteOwnershipRecord routeRecord,
            String sourceGatewayOwner) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(routeRecord, "routeRecord must not be null");
        String normalizedGatewayOwner = requireValue(sourceGatewayOwner, "source_gateway_owner");
        return new RelayEnvelope(
                event.tenantId(),
                event.clientId(),
                event.sessionId(),
                event.turnId(),
                event.seq(),
                event.topic(),
                event.traceId(),
                routeRecord.routeVersion(),
                normalizedGatewayOwner,
                routeRecord.skillOwner(),
                routeRecord.gatewayOwner(),
                HOP_GATEWAY_TO_SKILL_OWNER,
                partitionKey(event.tenantId(), event.sessionId()),
                dedupeTuple(event.sessionId(), event.turnId(), event.seq(), event.topic()),
                event.actor(),
                event.eventType(),
                event.payload(),
                event.reasonCode(),
                event.nextAction());
    }

    public static String partitionKey(String tenantId, String sessionId) {
        return requireValue(tenantId, "tenant_id") + ":" + requireValue(sessionId, "session_id");
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

    private static String requireValue(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
