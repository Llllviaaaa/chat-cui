package com.chatcui.skill.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SendbackRequest(
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("turn_id") String turnId,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("conversation_id") String conversationId,
        @JsonProperty("selected_text") String selectedText,
        @JsonProperty("message_text") String messageText
) {
}

