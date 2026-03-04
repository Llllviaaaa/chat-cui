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
        @JsonProperty("topic") String topic) {
}
