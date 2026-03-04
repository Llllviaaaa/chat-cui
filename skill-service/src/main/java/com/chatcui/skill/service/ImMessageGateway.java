package com.chatcui.skill.service;

import java.time.Instant;

public interface ImMessageGateway {

    ImSendResult send(ImSendCommand command);

    record ImSendCommand(
            String tenantId,
            String clientId,
            String conversationId,
            String traceId,
            String messageText
    ) {
    }

    record ImSendResult(String messageId, Instant sentAt) {
    }

    class ImSendException extends RuntimeException {
        private final String code;

        public ImSendException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}

