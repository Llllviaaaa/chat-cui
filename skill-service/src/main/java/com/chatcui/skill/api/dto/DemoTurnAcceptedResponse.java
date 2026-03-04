package com.chatcui.skill.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DemoTurnAcceptedResponse(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("turn_id") String turnId,
        @JsonProperty("receive_state") String receiveState,
        @JsonProperty("accepted_at") String acceptedAt) {
}
