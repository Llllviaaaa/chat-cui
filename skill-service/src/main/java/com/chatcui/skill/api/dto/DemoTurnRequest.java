package com.chatcui.skill.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DemoTurnRequest(
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("prompt") String prompt) {
}
