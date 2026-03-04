package com.chatcui.skill.service;

import com.chatcui.skill.api.dto.SkillTurnEventRequest;
import com.chatcui.skill.observability.FailureClass;
import com.chatcui.skill.observability.SkillMetricsRecorder;
import com.chatcui.skill.persistence.mapper.TurnRecordMapper;
import com.chatcui.skill.persistence.model.TurnRecord;
import com.chatcui.skill.relay.RelayDispatchService;
import com.chatcui.skill.relay.RelayEventConsumer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

    @Test
    void relayMetricsDistinguishRelaySuccessTimeoutOwnerFenceAndReplayWindowExpiry() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SkillMetricsRecorder metricsRecorder = new SkillMetricsRecorder(meterRegistry);
        RelayEventConsumer.AckClient noOpAck = (streamKey, group, messageId) -> {
        };

        RelayDispatchService dispatchedService = new RelayDispatchService(
                (tenantId, sessionId) -> Optional.of(new RelayDispatchService.RouteSnapshot(
                        tenantId,
                        sessionId,
                        12L,
                        "skill-owner-a",
                        "gateway-owner-b")),
                (targetGatewayOwner, relayEvent) -> {
                });
        RelayEventConsumer dispatchedConsumer = new RelayEventConsumer(
                "relay-group",
                "skill-owner-a",
                dispatchedService,
                new RelayEventConsumer.InMemoryTupleDedupeStore(),
                noOpAck,
                metricsRecorder);

        RelayDispatchService timeoutService = new RelayDispatchService(
                (tenantId, sessionId) -> Optional.of(new RelayDispatchService.RouteSnapshot(
                        tenantId,
                        sessionId,
                        12L,
                        "skill-owner-a",
                        "gateway-owner-b")),
                (targetGatewayOwner, relayEvent) -> {
                    throw new IllegalStateException("dispatch timeout");
                });
        RelayEventConsumer timeoutConsumer = new RelayEventConsumer(
                "relay-group",
                "skill-owner-a",
                timeoutService,
                new RelayEventConsumer.InMemoryTupleDedupeStore(),
                noOpAck,
                metricsRecorder);

        RelayDispatchService notOwnerService = new RelayDispatchService(
                (tenantId, sessionId) -> Optional.of(new RelayDispatchService.RouteSnapshot(
                        tenantId,
                        sessionId,
                        12L,
                        "skill-owner-b",
                        "gateway-owner-b")),
                (targetGatewayOwner, relayEvent) -> {
                });
        RelayEventConsumer notOwnerConsumer = new RelayEventConsumer(
                "relay-group",
                "skill-owner-a",
                notOwnerService,
                new RelayEventConsumer.InMemoryTupleDedupeStore(),
                noOpAck,
                metricsRecorder);

        RelayDispatchService missingRouteService =
                new RelayDispatchService((tenantId, sessionId) -> Optional.empty(), (targetGatewayOwner, relayEvent) -> {
                });
        RelayEventConsumer replayExpiredConsumer = new RelayEventConsumer(
                "relay-group",
                "skill-owner-a",
                missingRouteService,
                new RelayEventConsumer.InMemoryTupleDedupeStore(),
                noOpAck,
                (dedupeTuple, now) -> RelayEventConsumer.RecoveryDecision.REPLAY_WINDOW_EXPIRED,
                java.time.Clock.systemUTC(),
                metricsRecorder);

        dispatchedConsumer.consume(streamRecord("1-0", "turn-1", 1L));
        RelayEventConsumer.ConsumeOutcome timeout = timeoutConsumer.consume(streamRecord("2-0", "turn-2", 2L));
        RelayEventConsumer.ConsumeOutcome notOwner = notOwnerConsumer.consume(streamRecord("3-0", "turn-3", 3L));
        RelayEventConsumer.ConsumeOutcome replayExpired = replayExpiredConsumer.consume(streamRecord("4-0", "turn-4", 4L));

        org.junit.jupiter.api.Assertions.assertEquals(RelayEventConsumer.ConsumeStatus.PENDING_RETRY, timeout.status());
        org.junit.jupiter.api.Assertions.assertEquals(RelayEventConsumer.ConsumeStatus.SKIPPED_NOT_OWNER, notOwner.status());
        org.junit.jupiter.api.Assertions.assertEquals(RelayEventConsumer.ConsumeStatus.REPLAY_WINDOW_EXPIRED, replayExpired.status());
        assertRelayOutcome(meterRegistry, "relay_success", false, 1.0);
        assertRelayOutcome(meterRegistry, "relay_timeout", true, 1.0);
        assertRelayOutcome(meterRegistry, "owner_fenced", true, 1.0);
        assertRelayOutcome(meterRegistry, "replay_window_expired", false, 1.0);
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

    private RelayEventConsumer.StreamRecord streamRecord(String messageId, String turnId, long seq) {
        return new RelayEventConsumer.StreamRecord(
                "chatcui:relay:first-hop:{tenant-1:session-1}",
                messageId,
                new RelayDispatchService.RelayEvent(
                        "tenant-1",
                        "client-1",
                        "session-1",
                        turnId,
                        seq,
                        "skill.turn.delta",
                        "trace-" + seq,
                        12L,
                        "gateway-owner-a",
                        "skill-owner-a",
                        "gateway-owner-b",
                        "gateway_to_skill_owner",
                        "tenant-1:session-1",
                        "session-1|" + turnId + "|" + seq + "|skill.turn.delta",
                        "assistant",
                        "delta",
                        "payload-" + seq,
                        null,
                        null));
    }

    private void assertRelayOutcome(
            SimpleMeterRegistry meterRegistry,
            String outcome,
            boolean retryable,
            double expected) {
        double actual = meterRegistry.find("chatcui.skill.relay.outcomes")
                .tags(
                        "component", "skill-service.relay",
                        "outcome", outcome,
                        "failure_class", FailureClass.BRIDGE.value(),
                        "retryable", Boolean.toString(retryable))
                .counter()
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
