package com.chatcui.gateway.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import org.junit.jupiter.api.Test;

class DeliveryAckStateMachineTest {

    @Test
    void acceptedThenDeliveredUsesDeterministicTwoStageTransitions() {
        DeliveryAckStateMachine stateMachine = new DeliveryAckStateMachine();
        SkillTurnForwardEvent event = sampleEvent(3L);

        DeliveryAckStateMachine.AckSnapshot accepted = stateMachine.markGatewayOwnerAccepted(event, 12L);
        DeliveryAckStateMachine.AckSnapshot delivered = stateMachine.markClientDelivered(event, 12L);

        assertEquals(DeliveryAckStateMachine.Stage.GATEWAY_OWNER_ACCEPTED, accepted.stage());
        assertEquals(DeliveryAckStateMachine.Stage.CLIENT_DELIVERED, delivered.stage());
        assertEquals("gateway_owner_accepted", accepted.stageValue());
        assertEquals("client_delivered", delivered.stageValue());
        assertEquals("trace-3", delivered.traceId());
        assertEquals(12L, delivered.routeVersion());
        assertEquals("session-a|turn-a|3|skill.turn.delta", delivered.deliveryTuple());
    }

    @Test
    void timeoutBranchCarriesDeterministicTerminalEnvelopeFields() {
        DeliveryAckStateMachine stateMachine = new DeliveryAckStateMachine();
        SkillTurnForwardEvent event = sampleEvent(8L);
        stateMachine.markGatewayOwnerAccepted(event, 44L);

        DeliveryAckStateMachine.AckSnapshot timeout = stateMachine.markClientDeliveryTimeout(
                event,
                44L,
                "RELAY_CLIENT_DELIVERY_TIMEOUT",
                "retry_via_route_recheck");

        assertEquals(DeliveryAckStateMachine.Stage.CLIENT_DELIVERY_TIMEOUT, timeout.stage());
        assertEquals("client_delivery_timeout", timeout.stageValue());
        assertEquals("RELAY_CLIENT_DELIVERY_TIMEOUT", timeout.errorCode());
        assertEquals("retry_via_route_recheck", timeout.nextAction());
        assertEquals("trace-8", timeout.traceId());
        assertEquals(44L, timeout.routeVersion());
    }

    @Test
    void timeoutIsTerminalAndCannotTransitionToDelivered() {
        DeliveryAckStateMachine stateMachine = new DeliveryAckStateMachine();
        SkillTurnForwardEvent event = sampleEvent(10L);
        stateMachine.markGatewayOwnerAccepted(event, 7L);
        stateMachine.markClientDeliveryTimeout(event, 7L, "ROUTE_REPLAY_WINDOW_EXPIRED", "restart_session");

        DeliveryAckStateMachine.AckSnapshot deliveredAfterTimeout = stateMachine.markClientDelivered(event, 7L);

        assertEquals(DeliveryAckStateMachine.Stage.CLIENT_DELIVERY_TIMEOUT, deliveredAfterTimeout.stage());
        assertEquals("client_delivery_timeout", deliveredAfterTimeout.stageValue());
        assertEquals("ROUTE_REPLAY_WINDOW_EXPIRED", deliveredAfterTimeout.errorCode());
        assertEquals("restart_session", deliveredAfterTimeout.nextAction());
        assertTrue(stateMachine.current(event).isPresent());
    }

    private SkillTurnForwardEvent sampleEvent(long seq) {
        return new SkillTurnForwardEvent(
                "tenant-a",
                "client-a",
                "session-a",
                "turn-a",
                seq,
                "trace-" + seq,
                "assistant",
                "delta",
                "payload-" + seq,
                "skill.turn.delta");
    }
}
