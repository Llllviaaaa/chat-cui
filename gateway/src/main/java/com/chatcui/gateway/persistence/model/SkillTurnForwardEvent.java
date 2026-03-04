package com.chatcui.gateway.persistence.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SkillTurnForwardEvent(
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("turn_id") String turnId,
        @JsonProperty("seq") long seq,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("actor") String actor,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("payload") String payload,
        @JsonProperty("topic") String topic,
        @JsonProperty("reason_code") String reasonCode,
        @JsonProperty("next_action") String nextAction) {

    public SkillTurnForwardEvent(
            String tenantId,
            String clientId,
            String sessionId,
            String turnId,
            long seq,
            String traceId,
            String actor,
            String eventType,
            String payload,
            String topic) {
        this(tenantId, clientId, sessionId, turnId, seq, traceId, actor, eventType, payload, topic, null, null);
    }
}
