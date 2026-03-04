package com.chatcui.skill.persistence.model;

import java.time.LocalDateTime;

public record TurnRecord(
        String tenantId,
        String clientId,
        String sessionId,
        String turnId,
        Long seq,
        String traceId,
        String actor,
        String eventType,
        String payload,
        String turnStatus,
        String deliveryStatus,
        LocalDateTime createdAt
) {
}
