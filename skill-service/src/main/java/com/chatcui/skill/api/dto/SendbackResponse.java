package com.chatcui.skill.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SendbackResponse(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("turn_id") String turnId,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("send_status") String sendStatus,
        @JsonProperty("im_message_id") String imMessageId,
        @JsonProperty("sent_at") String sentAt
) {
}

