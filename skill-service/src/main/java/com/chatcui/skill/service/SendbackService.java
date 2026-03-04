package com.chatcui.skill.service;

import com.chatcui.skill.api.dto.SendbackResponse;
import com.chatcui.skill.observability.FailureClass;
import com.chatcui.skill.observability.SkillMetricsRecorder;
import com.chatcui.skill.persistence.mapper.SendbackRecordMapper;
import com.chatcui.skill.persistence.mapper.TurnRecordMapper;
import com.chatcui.skill.persistence.model.SendbackRecord;
import com.chatcui.skill.persistence.model.TurnRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class SendbackService {

    private final TurnRecordMapper turnRecordMapper;
    private final SendbackRecordMapper sendbackRecordMapper;
    private final ImMessageGateway imMessageGateway;
    private final Clock clock;
    private final SkillMetricsRecorder skillMetricsRecorder;
    private String idempotencyHashAlgorithm;

    public SendbackService(
            TurnRecordMapper turnRecordMapper,
            SendbackRecordMapper sendbackRecordMapper,
            ImMessageGateway imMessageGateway,
            SkillMetricsRecorder skillMetricsRecorder
    ) {
        this(turnRecordMapper, sendbackRecordMapper, imMessageGateway, Clock.systemUTC(), "SHA-256", skillMetricsRecorder);
    }

    SendbackService(
            TurnRecordMapper turnRecordMapper,
            SendbackRecordMapper sendbackRecordMapper,
            ImMessageGateway imMessageGateway
    ) {
        this(turnRecordMapper, sendbackRecordMapper, imMessageGateway, Clock.systemUTC(), "SHA-256", SkillMetricsRecorder.noop());
    }

    SendbackService(
            TurnRecordMapper turnRecordMapper,
            SendbackRecordMapper sendbackRecordMapper,
            ImMessageGateway imMessageGateway,
            Clock clock
    ) {
        this(turnRecordMapper, sendbackRecordMapper, imMessageGateway, clock, "SHA-256", SkillMetricsRecorder.noop());
    }

    SendbackService(
            TurnRecordMapper turnRecordMapper,
            SendbackRecordMapper sendbackRecordMapper,
            ImMessageGateway imMessageGateway,
            Clock clock,
            SkillMetricsRecorder skillMetricsRecorder
    ) {
        this(turnRecordMapper, sendbackRecordMapper, imMessageGateway, clock, "SHA-256", skillMetricsRecorder);
    }

    SendbackService(
            TurnRecordMapper turnRecordMapper,
            SendbackRecordMapper sendbackRecordMapper,
            ImMessageGateway imMessageGateway,
            Clock clock,
            String idempotencyHashAlgorithm
    ) {
        this(turnRecordMapper, sendbackRecordMapper, imMessageGateway, clock, idempotencyHashAlgorithm, SkillMetricsRecorder.noop());
    }

    SendbackService(
            TurnRecordMapper turnRecordMapper,
            SendbackRecordMapper sendbackRecordMapper,
            ImMessageGateway imMessageGateway,
            Clock clock,
            String idempotencyHashAlgorithm,
            SkillMetricsRecorder skillMetricsRecorder
    ) {
        this.turnRecordMapper = turnRecordMapper;
        this.sendbackRecordMapper = sendbackRecordMapper;
        this.imMessageGateway = imMessageGateway;
        this.clock = clock;
        this.skillMetricsRecorder = skillMetricsRecorder == null ? SkillMetricsRecorder.noop() : skillMetricsRecorder;
        this.idempotencyHashAlgorithm = normalizeHashAlgorithm(idempotencyHashAlgorithm);
    }

    @Value("${skill.sendback.idempotency.hash-algorithm:SHA-256}")
    void setIdempotencyHashAlgorithm(String idempotencyHashAlgorithm) {
        this.idempotencyHashAlgorithm = normalizeHashAlgorithm(idempotencyHashAlgorithm);
    }

    public SendbackResponse send(SendCommand command) {
        long startedNanos = System.nanoTime();
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
        String idempotencyKey = deriveIdempotencyKey(command, selectedText, messageText);
        Optional<SendbackRecord> existing = sendbackRecordMapper.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            boolean retryable = "failed".equalsIgnoreCase(existing.get().sendStatus())
                    && FailureClass.SENDBACK.retryableDefault();
            recordSendbackMetric("dedup", retryable, startedNanos);
            return replayExistingResult(existing.get(), command.sessionId(), command.turnId());
        }

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
                    idempotencyKey,
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
            recordSendbackMetric("success", false, startedNanos);
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
                    idempotencyKey,
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
            recordSendbackMetric("failure", FailureClass.SENDBACK.retryableDefault(), startedNanos);
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

    private String deriveIdempotencyKey(SendCommand command, String selectedText, String messageText) {
        String contentFingerprint = hashHex(selectedText + "\n" + messageText);
        String keySource = String.join(
                "|",
                command.tenantId(),
                command.clientId(),
                command.sessionId(),
                command.turnId(),
                command.conversationId(),
                contentFingerprint
        );
        return "idem-" + hashHex(keySource);
    }

    private String hashHex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(idempotencyHashAlgorithm);
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("unsupported idempotency hash algorithm: " + idempotencyHashAlgorithm, ex);
        }
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

    private SendbackResponse replayExistingResult(SendbackRecord existing, String sessionId, String turnId) {
        if ("failed".equalsIgnoreCase(existing.sendStatus())) {
            String code = existing.errorCode() == null || existing.errorCode().isBlank()
                    ? "IM_SEND_FAILED"
                    : existing.errorCode();
            String message = existing.errorMessage() == null || existing.errorMessage().isBlank()
                    ? "IM sendback request failed previously."
                    : existing.errorMessage();
            throw new SendbackFailedException(code, message);
        }
        return new SendbackResponse(
                existing.requestId(),
                sessionId,
                turnId,
                normalizeTraceId(existing.traceId(), null),
                existing.sendStatus(),
                existing.imMessageId(),
                formatCreatedAt(existing.createdAt())
        );
    }

    private String formatCreatedAt(LocalDateTime createdAt) {
        if (createdAt == null) {
            return Instant.now(clock).toString();
        }
        return createdAt.toInstant(ZoneOffset.UTC).toString();
    }

    private String normalizeHashAlgorithm(String hashAlgorithm) {
        if (hashAlgorithm == null || hashAlgorithm.isBlank()) {
            return "SHA-256";
        }
        return hashAlgorithm.trim();
    }

    private void recordSendbackMetric(String outcome, boolean retryable, long startedNanos) {
        skillMetricsRecorder.recordSendbackOutcome(
                outcome,
                FailureClass.SENDBACK,
                retryable,
                System.nanoTime() - startedNanos);
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
