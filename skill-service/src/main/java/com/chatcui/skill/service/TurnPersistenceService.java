package com.chatcui.skill.service;

import com.chatcui.skill.api.dto.SkillTurnEventRequest;
import com.chatcui.skill.persistence.mapper.TurnRecordMapper;
import com.chatcui.skill.persistence.model.TurnRecord;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class TurnPersistenceService {

    private final TurnRecordMapper turnRecordMapper;

    public TurnPersistenceService(TurnRecordMapper turnRecordMapper) {
        this.turnRecordMapper = turnRecordMapper;
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

        turnRecordMapper.insert(toRecord(request));
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
}
