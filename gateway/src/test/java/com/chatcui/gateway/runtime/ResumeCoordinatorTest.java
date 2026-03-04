package com.chatcui.gateway.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResumeCoordinatorTest {

    @Test
    void firstTupleContinuesAndStoresAnchor() {
        ResumeCoordinator coordinator = new ResumeCoordinator();

        ResumeDecision decision = coordinator.evaluate("session-a", "turn-a", 1L, "owner-a");

        assertEquals(ResumeDecision.Outcome.CONTINUE, decision.outcome());
        assertTrue(decision.allowForward());
        ResumeAnchor stored = coordinator.lastAnchor("session-a").orElseThrow();
        assertEquals("session-a", stored.sessionId());
        assertEquals("turn-a", stored.turnId());
        assertEquals(1L, stored.seq());
    }

    @Test
    void duplicateTupleIsDroppedWithDiagnostics() {
        ResumeCoordinator coordinator = new ResumeCoordinator();
        coordinator.evaluate("session-a", "turn-a", 3L, "owner-a");

        ResumeDecision decision = coordinator.evaluate("session-a", "turn-a", 3L, "owner-a");

        assertEquals(ResumeDecision.Outcome.DROP_DUPLICATE, decision.outcome());
        assertTrue(!decision.allowForward());
        assertEquals("SEQ_DUPLICATE_DROPPED", decision.reasonCode());
        assertEquals("ignore_duplicate", decision.nextAction());
        assertEquals(3L, decision.diagnostics().get("previous_seq"));
        assertEquals(3L, decision.diagnostics().get("incoming_seq"));
    }

    @Test
    void gapTupleRequestsCompensationAndBlocksForwarding() {
        ResumeCoordinator coordinator = new ResumeCoordinator();
        coordinator.evaluate("session-a", "turn-a", 2L, "owner-a");

        ResumeDecision decision = coordinator.evaluate("session-a", "turn-a", 5L, "owner-a");

        assertEquals(ResumeDecision.Outcome.COMPENSATE_GAP, decision.outcome());
        assertTrue(!decision.allowForward());
        assertEquals("SEQ_GAP_COMPENSATION_REQUIRED", decision.reasonCode());
        assertEquals("compensate_and_resume", decision.nextAction());
        assertEquals(3L, decision.diagnostics().get("expected_seq"));
        assertEquals(5L, decision.diagnostics().get("incoming_seq"));
    }

    @Test
    void conflictingOwnerReturnsTerminalDecisionWithNextAction() {
        ResumeCoordinator coordinator = new ResumeCoordinator();
        coordinator.evaluate("session-a", "turn-a", 1L, "owner-a");

        ResumeDecision decision = coordinator.evaluate("session-a", "turn-a", 2L, "owner-b");

        assertEquals(ResumeDecision.Outcome.TERMINAL_FAILURE, decision.outcome());
        assertTrue(!decision.allowForward());
        assertEquals("RESUME_OWNER_CONFLICT", decision.reasonCode());
        assertEquals("restart_session", decision.nextAction());
        assertEquals("owner-a", decision.diagnostics().get("active_owner"));
        assertEquals("owner-b", decision.diagnostics().get("incoming_owner"));
    }
}
