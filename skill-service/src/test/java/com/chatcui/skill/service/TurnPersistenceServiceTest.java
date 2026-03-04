package com.chatcui.skill.service;

import com.chatcui.skill.api.dto.SkillTurnEventRequest;
import com.chatcui.skill.observability.FailureClass;
import com.chatcui.skill.persistence.mapper.TurnRecordMapper;
import com.chatcui.skill.persistence.model.TurnRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TurnPersistenceServiceTest {

    @Mock
    private TurnRecordMapper turnRecordMapper;

    private TurnPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new TurnPersistenceService(turnRecordMapper);
    }

    @Test
    void failureClassContractUsesCanonicalTaxonomy() {
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.List.of("auth", "bridge", "persistence", "sendback", "unknown"),
                Arrays.stream(FailureClass.values()).map(FailureClass::value).toList());
    }

    @Test
    void replayedEventWithSameSessionTurnSeqDoesNotInsertDuplicate() {
        SkillTurnEventRequest replayed = event("session-1", "turn-1", 3L, SkillTurnEventRequest.EventType.FINAL, "hello");
        when(turnRecordMapper.existsBySessionTurnSeq("session-1", "turn-1", 3L)).thenReturn(true);

        service.persist(replayed);

        verify(turnRecordMapper, never()).insert(any(TurnRecord.class));
    }

    @Test
    void lowerSeqDoesNotOverwriteNewerSnapshot() {
        SkillTurnEventRequest stale = event("session-1", "turn-1", 2L, SkillTurnEventRequest.EventType.DELTA, "old");
        when(turnRecordMapper.existsBySessionTurnSeq("session-1", "turn-1", 2L)).thenReturn(false);
        when(turnRecordMapper.findLatestBySessionTurn("session-1", "turn-1"))
                .thenReturn(Optional.of(new TurnRecord("tenant-1", "client-1", "session-1", "turn-1", 5L, "trace-5", "assistant", "final", "new", "in_progress", "pending", null)));

        service.persist(stale);

        verify(turnRecordMapper, never()).insert(any(TurnRecord.class));
    }

    @Test
    void statusTransitionsUpdateSnapshotAndDeliveryStatus() {
        SkillTurnEventRequest inProgress = event("session-1", "turn-1", 4L, SkillTurnEventRequest.EventType.FINAL, "draft");
        SkillTurnEventRequest completed = event("session-1", "turn-1", 5L, SkillTurnEventRequest.EventType.COMPLETED, "done");
        SkillTurnEventRequest errored = event("session-1", "turn-1", 6L, SkillTurnEventRequest.EventType.ERROR, "boom");

        when(turnRecordMapper.existsBySessionTurnSeq(eq("session-1"), eq("turn-1"), any(Long.class))).thenReturn(false);
        when(turnRecordMapper.findLatestBySessionTurn("session-1", "turn-1"))
                .thenReturn(Optional.empty(), Optional.of(new TurnRecord("tenant-1", "client-1", "session-1", "turn-1", 4L, "trace-4", "assistant", "final", "draft", "in_progress", "pending", null)));

        service.persist(inProgress);
        service.persist(completed);
        service.persist(errored);

        verify(turnRecordMapper, org.mockito.Mockito.times(3)).insert(any(TurnRecord.class));
        verify(turnRecordMapper).insert(argThatRecord("completed", "delivered", "done", 5L));
        verify(turnRecordMapper).insert(argThatRecord("error", "failed", "boom", 6L));
    }

    @Test
    void persistenceFailureEmitsStructuredFailureEnvelopeWithoutPayloadLeakage() {
        AtomicReference<Map<String, Object>> capturedFailure = new AtomicReference<>();
        TurnPersistenceService loggingService = new TurnPersistenceService(turnRecordMapper, capturedFailure::set);
        SkillTurnEventRequest request = event("session-1", "turn-1", 8L, SkillTurnEventRequest.EventType.FINAL, "sensitive payload");

        when(turnRecordMapper.existsBySessionTurnSeq("session-1", "turn-1", 8L)).thenReturn(false);
        when(turnRecordMapper.findLatestBySessionTurn("session-1", "turn-1")).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("db down")).when(turnRecordMapper).insert(any(TurnRecord.class));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> loggingService.persist(request));
        Map<String, Object> envelope = capturedFailure.get();
        org.junit.jupiter.api.Assertions.assertNotNull(envelope);
        org.junit.jupiter.api.Assertions.assertEquals("tenant-1", envelope.get("tenant_id"));
        org.junit.jupiter.api.Assertions.assertEquals("client-1", envelope.get("client_id"));
        org.junit.jupiter.api.Assertions.assertEquals("session-1", envelope.get("session_id"));
        org.junit.jupiter.api.Assertions.assertEquals("turn-1", envelope.get("turn_id"));
        org.junit.jupiter.api.Assertions.assertEquals(8L, envelope.get("seq"));
        org.junit.jupiter.api.Assertions.assertEquals("trace-8", envelope.get("trace_id"));
        org.junit.jupiter.api.Assertions.assertEquals("TURN_PERSISTENCE_WRITE_FAILED", envelope.get("error_code"));
        org.junit.jupiter.api.Assertions.assertEquals("skill-service.turn-persistence", envelope.get("component"));
        org.junit.jupiter.api.Assertions.assertEquals("failed", envelope.get("status"));
        org.junit.jupiter.api.Assertions.assertEquals("persistence", envelope.get("failure_class"));
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, envelope.get("retryable"));
        org.junit.jupiter.api.Assertions.assertFalse(envelope.containsKey("payload"));
        org.junit.jupiter.api.Assertions.assertFalse(envelope.toString().contains("sensitive payload"));
    }

    private SkillTurnEventRequest event(String sessionId, String turnId, Long seq, SkillTurnEventRequest.EventType eventType, String payload) {
        return new SkillTurnEventRequest(
                "tenant-1",
                "client-1",
                sessionId,
                turnId,
                seq,
                "trace-" + seq,
                SkillTurnEventRequest.Actor.ASSISTANT,
                eventType,
                payload
        );
    }

    private TurnRecord argThatRecord(String turnStatus, String deliveryStatus, String payload, Long seq) {
        return org.mockito.ArgumentMatchers.argThat(record ->
                record.turnStatus().equals(turnStatus)
                        && record.deliveryStatus().equals(deliveryStatus)
                        && record.payload().equals(payload)
                        && record.seq().equals(seq)
        );
    }
}
