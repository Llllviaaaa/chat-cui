package com.chatcui.skill.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public record SkillTurnEventRequest(
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("turn_id") String turnId,
        @JsonProperty("seq") Long seq,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("actor") Actor actor,
        @JsonProperty("event_type") EventType eventType,
        @JsonProperty("payload") String payload
) {
    public enum Actor {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system"),
        PLUGIN("plugin");

        private final String value;

        Actor(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static Actor fromValue(String raw) {
            if (raw == null) {
                return null;
            }
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        }
    }

    public enum EventType {
        DELTA("delta"),
        FINAL("final"),
        COMPLETED("completed"),
        ERROR("error");

        private final String value;

        EventType(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static EventType fromValue(String raw) {
            if (raw == null) {
                return null;
            }
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        }
    }
}
