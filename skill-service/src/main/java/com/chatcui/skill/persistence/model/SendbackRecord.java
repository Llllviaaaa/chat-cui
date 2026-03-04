package com.chatcui.skill.persistence.model;

import java.time.LocalDateTime;

public record SendbackRecord(
        String requestId,
        String tenantId,
        String clientId,
        String sessionId,
        String turnId,
        String traceId,
        String conversationId,
        String selectedText,
        String messageText,
        String sendStatus,
        String imMessageId,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt
) {
}

