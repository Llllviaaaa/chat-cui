package com.chatcui.skill.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.skill.relay.RelayDispatchService;
import com.chatcui.skill.relay.RelayEventConsumer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class CrossInstanceRelayIntegrationTest {

    @Test
    void gatewayToSkillOwnerRelayDispatchesToRouteSelectedGatewayClient() {
        List<DispatchCall> dispatchCalls = new CopyOnWriteArrayList<>();
        List<AckCall> ackCalls = new CopyOnWriteArrayList<>();
        RelayDispatchService dispatchService = new RelayDispatchService(
                (tenantId, sessionId) -> Optional.of(new RelayDispatchService.RouteSnapshot(
                        tenantId,
                        sessionId,
                        12L,
                        "skill-owner-a",
                        "gateway-owner-b")),
                (targetGatewayOwner, event) -> dispatchCalls.add(new DispatchCall(targetGatewayOwner, event.clientId(), event.traceId())));
        RelayEventConsumer consumer = new RelayEventConsumer(
                "relay-first-hop",
                "skill-owner-a",
                dispatchService,
                new RelayEventConsumer.InMemoryTupleDedupeStore(),
                (streamKey, group, messageId) -> ackCalls.add(new AckCall(streamKey, group, messageId)));

        RelayDispatchService.RelayEvent event = firstHopEvent();
        RelayEventConsumer.ConsumeOutcome outcome = consumer.consume(new RelayEventConsumer.StreamRecord(
                "chatcui:relay:first-hop:{tenant-a:session-a}",
                "1-0",
                event));

        assertEquals(RelayEventConsumer.ConsumeStatus.DISPATCHED, outcome.status());
        assertTrue(outcome.acked());
        assertEquals(1, dispatchCalls.size());
        assertEquals("gateway-owner-b", dispatchCalls.getFirst().targetGatewayOwner());
        assertEquals("client-a", dispatchCalls.getFirst().clientId());
        assertEquals(1, ackCalls.size());
        assertEquals("1-0", ackCalls.getFirst().messageId());
    }

    @Test
    void duplicateTupleIsSuppressedAcrossFirstHopConsumePath() {
        List<DispatchCall> dispatchCalls = new CopyOnWriteArrayList<>();
        List<AckCall> ackCalls = new CopyOnWriteArrayList<>();
        RelayDispatchService dispatchService = new RelayDispatchService(
                (tenantId, sessionId) -> Optional.of(new RelayDispatchService.RouteSnapshot(
                        tenantId,
                        sessionId,
                        12L,
                        "skill-owner-a",
                        "gateway-owner-b")),
                (targetGatewayOwner, event) -> dispatchCalls.add(new DispatchCall(targetGatewayOwner, event.clientId(), event.traceId())));
        RelayEventConsumer consumer = new RelayEventConsumer(
                "relay-first-hop",
                "skill-owner-a",
                dispatchService,
                new RelayEventConsumer.InMemoryTupleDedupeStore(),
                (streamKey, group, messageId) -> ackCalls.add(new AckCall(streamKey, group, messageId)));

        RelayDispatchService.RelayEvent event = firstHopEvent();
        RelayEventConsumer.ConsumeOutcome first = consumer.consume(new RelayEventConsumer.StreamRecord(
                "chatcui:relay:first-hop:{tenant-a:session-a}",
                "1-0",
                event));
        RelayEventConsumer.ConsumeOutcome duplicate = consumer.consume(new RelayEventConsumer.StreamRecord(
                "chatcui:relay:first-hop:{tenant-a:session-a}",
                "2-0",
                event));

        assertEquals(RelayEventConsumer.ConsumeStatus.DISPATCHED, first.status());
        assertEquals(RelayEventConsumer.ConsumeStatus.DUPLICATE_DROPPED, duplicate.status());
        assertTrue(first.acked());
        assertTrue(duplicate.acked());
        assertEquals(1, dispatchCalls.size());
        assertEquals(2, ackCalls.size());
    }

    @Test
    void dispatchFailureKeepsStreamMessagePendingForRecovery() {
        List<AckCall> ackCalls = new CopyOnWriteArrayList<>();
        RelayDispatchService dispatchService = new RelayDispatchService(
                (tenantId, sessionId) -> Optional.of(new RelayDispatchService.RouteSnapshot(
                        tenantId,
                        sessionId,
                        12L,
                        "skill-owner-a",
                        "gateway-owner-b")),
                (targetGatewayOwner, event) -> {
                    throw new IllegalStateException("gateway unreachable");
                });
        RelayEventConsumer consumer = new RelayEventConsumer(
                "relay-first-hop",
                "skill-owner-a",
                dispatchService,
                new RelayEventConsumer.InMemoryTupleDedupeStore(),
                (streamKey, group, messageId) -> ackCalls.add(new AckCall(streamKey, group, messageId)));

        RelayEventConsumer.ConsumeOutcome outcome = consumer.consume(new RelayEventConsumer.StreamRecord(
                "chatcui:relay:first-hop:{tenant-a:session-a}",
                "1-0",
                firstHopEvent()));

        assertEquals(RelayEventConsumer.ConsumeStatus.PENDING_RETRY, outcome.status());
        assertFalse(outcome.acked());
        assertTrue(ackCalls.isEmpty());
    }

    private RelayDispatchService.RelayEvent firstHopEvent() {
        return new RelayDispatchService.RelayEvent(
                "tenant-a",
                "client-a",
                "session-a",
                "turn-shared",
                1L,
                "skill.turn.delta",
                "trace-a",
                12L,
                "gateway-owner-a",
                "skill-owner-a",
                "gateway-owner-b",
                "gateway_to_skill_owner",
                "tenant-a:session-a",
                "session-a|turn-shared|1|skill.turn.delta",
                "assistant",
                "delta",
                "payload-1",
                null,
                null);
    }

    private record DispatchCall(String targetGatewayOwner, String clientId, String traceId) {
    }

    private record AckCall(String streamKey, String group, String messageId) {
    }
}
