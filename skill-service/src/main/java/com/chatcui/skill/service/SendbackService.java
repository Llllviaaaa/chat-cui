package com.chatcui.skill.service;

import com.chatcui.skill.api.dto.SendbackResponse;
import com.chatcui.skill.persistence.mapper.SendbackRecordMapper;
import com.chatcui.skill.persistence.mapper.TurnRecordMapper;
import com.chatcui.skill.persistence.model.SendbackRecord;
import com.chatcui.skill.persistence.model.TurnRecord;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class SendbackService {

    private final TurnRecordMapper turnRecordMapper;
    private final SendbackRecordMapper sendbackRecordMapper;
    private final ImMessageGateway imMessageGateway;
    private final Clock clock;

    public SendbackService(
            TurnRecordMapper turnRecordMapper,
            SendbackRecordMapper sendbackRecordMapper,
            ImMessageGateway imMessageGateway
    ) {
        this(turnRecordMapper, sendbackRecordMapper, imMessageGateway, Clock.systemUTC());
    }

    SendbackService(
            TurnRecordMapper turnRecordMapper,
            SendbackRecordMapper sendbackRecordMapper,
            ImMessageGateway imMessageGateway,
            Clock clock
    ) {
        this.turnRecordMapper = turnRecordMapper;
        this.sendbackRecordMapper = sendbackRecordMapper;
        this.imMessageGateway = imMessageGateway;
        this.clock = clock;
    }

    public SendbackResponse send(SendCommand command) {
        validate(command);

        if (!turnRecordMapper.existsTurnInSession(
                command.tenantId(),
                command.clientId(),
                command.sessionId(),
                command.turnId()
        )) {
            throw new TurnNotFoundException(command.turnId());
        }

        TurnRecord source = turnRecordMapper.findLatestBySessionTurn(command.sessionId(), command.turnId())
                .orElseThrow(() -> new TurnNotFoundException(command.turnId()));
        if (!"assistant".equalsIgnoreCase(source.actor())) {
            throw new SelectionMismatchException("selected content must come from assistant output");
        }

        String selectedText = command.selectedText().trim();
        String sourcePayload = source.payload() == null ? "" : source.payload();
        if (!sourcePayload.contains(selectedText)) {
            throw new SelectionMismatchException("selected_text is not part of assistant output");
        }

        String messageText = normalizeMessageText(command.messageText(), selectedText);
        String traceId = normalizeTraceId(command.traceId(), source.traceId());
        String requestId = "sendback-" + UUID.randomUUID();

        try {
            ImMessageGateway.ImSendResult result = imMessageGateway.send(new ImMessageGateway.ImSendCommand(
                    command.tenantId(),
                    command.clientId(),
                    command.conversationId(),
                    traceId,
                    messageText
            ));
            Instant sentAt = result.sentAt() == null ? Instant.now(clock) : result.sentAt();
            sendbackRecordMapper.insert(new SendbackRecord(
                    requestId,
                    command.tenantId(),
                    command.clientId(),
                    command.sessionId(),
                    command.turnId(),
                    traceId,
                    command.conversationId(),
                    selectedText,
                    messageText,
                    "sent",
                    result.messageId(),
                    null,
                    null,
                    null
            ));
            return new SendbackResponse(
                    requestId,
                    command.sessionId(),
                    command.turnId(),
                    traceId,
                    "sent",
                    result.messageId(),
                    sentAt.toString()
            );
        } catch (ImMessageGateway.ImSendException ex) {
            sendbackRecordMapper.insert(new SendbackRecord(
                    requestId,
                    command.tenantId(),
                    command.clientId(),
                    command.sessionId(),
                    command.turnId(),
                    traceId,
                    command.conversationId(),
                    selectedText,
                    messageText,
                    "failed",
                    null,
                    ex.code(),
                    ex.getMessage(),
                    null
            ));
            throw new SendbackFailedException(ex.code(), ex.getMessage());
        }
    }

    private void validate(SendCommand command) {
        Objects.requireNonNull(command, "sendback command is required");
        requireValue(command.tenantId(), "tenant_id");
        requireValue(command.clientId(), "client_id");
        requireValue(command.sessionId(), "session_id");
        requireValue(command.turnId(), "turn_id");
        requireValue(command.conversationId(), "conversation_id");
        requireValue(command.selectedText(), "selected_text");
    }

    private String normalizeMessageText(String messageText, String selectedText) {
        String normalized = messageText == null ? selectedText : messageText.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("message_text is required");
        }
        return normalized;
    }

    private String normalizeTraceId(String traceId, String sourceTraceId) {
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        if (sourceTraceId != null && !sourceTraceId.isBlank()) {
            return sourceTraceId;
        }
        return "trace-sendback-" + UUID.randomUUID();
    }

    private void requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    public record SendCommand(
            String tenantId,
            String clientId,
            String sessionId,
            String turnId,
            String traceId,
            String conversationId,
            String selectedText,
            String messageText
    ) {
    }

    public static class TurnNotFoundException extends RuntimeException {
        public TurnNotFoundException(String turnId) {
            super("turn not found: " + turnId);
        }
    }

    public static class SelectionMismatchException extends RuntimeException {
        public SelectionMismatchException(String message) {
            super(message);
        }
    }

    public static class SendbackFailedException extends RuntimeException {
        private final String code;

        public SendbackFailedException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}

