package com.chatcui.skill.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SessionHistoryResponse(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore,
        @JsonProperty("items") List<HistoryItem> items
) {
    public record HistoryItem(
            @JsonProperty("turn_id") String turnId,
            @JsonProperty("seq") Long seq,
            @JsonProperty("trace_id") String traceId,
            @JsonProperty("actor") String actor,
            @JsonProperty("snapshot") String snapshot,
            @JsonProperty("turn_status") String turnStatus,
            @JsonProperty("delivery_status") String deliveryStatus,
            @JsonProperty("created_at") String createdAt
    ) {
    }
}
