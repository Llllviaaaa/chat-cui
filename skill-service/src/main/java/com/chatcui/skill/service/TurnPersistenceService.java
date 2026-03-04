package com.chatcui.skill.service;

import com.chatcui.skill.api.dto.SkillTurnEventRequest;
import com.chatcui.skill.observability.FailureClass;
import com.chatcui.skill.persistence.mapper.TurnRecordMapper;
import com.chatcui.skill.persistence.model.TurnRecord;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class TurnPersistenceService {
    private static final System.Logger LOGGER = System.getLogger(TurnPersistenceService.class.getName());

    private final TurnRecordMapper turnRecordMapper;
    private final FailureLogSink failureLogSink;

    public TurnPersistenceService(TurnRecordMapper turnRecordMapper) {
        this(turnRecordMapper, null);
    }

    TurnPersistenceService(TurnRecordMapper turnRecordMapper, FailureLogSink failureLogSink) {
        this.turnRecordMapper = turnRecordMapper;
        this.failureLogSink = failureLogSink == null ? this::logStructuredFailure : failureLogSink;
    }

    public void persist(SkillTurnEventRequest request) {
        validate(request);

        if (turnRecordMapper.existsBySessionTurnSeq(request.sessionId(), request.turnId(), request.seq())) {
            return;
        }

        Optional<TurnRecord> latest = turnRecordMapper.findLatestBySessionTurn(request.sessionId(), request.turnId());
        if (latest.isPresent() && latest.get().seq() >= request.seq()) {
            return;
        }

        try {
            turnRecordMapper.insert(toRecord(request));
        } catch (RuntimeException error) {
            failureLogSink.record(toFailureEnvelope(request));
            throw error;
        }
    }

    private TurnRecord toRecord(SkillTurnEventRequest request) {
        Status status = mapStatus(request.eventType());
        return new TurnRecord(
                request.tenantId(),
                request.clientId(),
                request.sessionId(),
                request.turnId(),
                request.seq(),
                request.traceId(),
                request.actor().value(),
                request.eventType().value(),
                request.payload(),
                status.turnStatus(),
                status.deliveryStatus(),
                null
        );
    }

    private Status mapStatus(SkillTurnEventRequest.EventType eventType) {
        return switch (eventType) {
            case DELTA, FINAL -> new Status("in_progress", "pending");
            case COMPLETED -> new Status("completed", "delivered");
            case ERROR -> new Status("error", "failed");
        };
    }

    private void validate(SkillTurnEventRequest request) {
        Objects.requireNonNull(request, "request is required");
        requireValue(request.tenantId(), "tenant_id");
        requireValue(request.clientId(), "client_id");
        requireValue(request.sessionId(), "session_id");
        requireValue(request.turnId(), "turn_id");
        requireValue(request.traceId(), "trace_id");
        Objects.requireNonNull(request.seq(), "seq is required");
        Objects.requireNonNull(request.actor(), "actor is required");
        Objects.requireNonNull(request.eventType(), "event_type is required");
    }

    private void requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private record Status(String turnStatus, String deliveryStatus) {
    }

    private void logStructuredFailure(Map<String, Object> envelope) {
        LOGGER.log(System.Logger.Level.ERROR, envelope.toString());
    }

    private Map<String, Object> toFailureEnvelope(SkillTurnEventRequest request) {
        return Map.ofEntries(
                Map.entry("tenant_id", stringOrUnknown(request.tenantId(), "tenant-unknown")),
                Map.entry("client_id", stringOrUnknown(request.clientId(), "client-unknown")),
                Map.entry("session_id", stringOrUnknown(request.sessionId(), "session-unknown")),
                Map.entry("turn_id", stringOrUnknown(request.turnId(), "turn-unknown")),
                Map.entry("seq", request.seq()),
                Map.entry("trace_id", stringOrUnknown(request.traceId(), "trace-unknown")),
                Map.entry("error_code", "TURN_PERSISTENCE_WRITE_FAILED"),
                Map.entry("component", "skill-service.turn-persistence"),
                Map.entry("status", "failed"),
                Map.entry("failure_class", FailureClass.PERSISTENCE.value()),
                Map.entry("retryable", FailureClass.PERSISTENCE.retryableDefault()));
    }

    private String stringOrUnknown(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    @FunctionalInterface
    interface FailureLogSink {
        void record(Map<String, Object> envelope);
    }
}
