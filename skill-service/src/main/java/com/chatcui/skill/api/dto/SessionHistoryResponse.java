package com.chatcui.skill.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SessionHistoryResponse(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("items") List<HistoryItem> items
) {
    public record HistoryItem(
            @JsonProperty("turn_id") String turnId,
            @JsonProperty("seq") Long seq,
            @JsonProperty("actor") String actor,
            @JsonProperty("content") String content,
            @JsonProperty("turn_status") String turnStatus,
            @JsonProperty("delivery_status") String deliveryStatus,
            @JsonProperty("created_at") String createdAt
    ) {
    }
}
