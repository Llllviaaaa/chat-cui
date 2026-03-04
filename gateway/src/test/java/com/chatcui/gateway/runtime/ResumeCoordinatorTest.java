package com.chatcui.gateway.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.routing.RouteCasResult;
import com.chatcui.gateway.routing.RouteOwnershipRecord;
import com.chatcui.gateway.routing.RouteOwnershipStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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

    @Test
    void fencedOwnerIsRejectedWithRouteVersionDiagnostics() {
        RouteOwnershipStore routeStore = fixedRouteStore(new RouteOwnershipRecord(
                "tenant-a",
                "session-a",
                19L,
                "skill-owner-a",
                "gateway-owner-b",
                "gateway-owner-a",
                Instant.parse("2026-03-04T00:00:00Z")));
        ResumeCoordinator coordinator = new ResumeCoordinator(routeStore, "gateway-owner-a");

        ResumeDecision decision = coordinator.evaluate("tenant-a", "session-a", "turn-a", 2L, "gateway-owner-a");

        assertEquals(ResumeDecision.Outcome.TERMINAL_FAILURE, decision.outcome());
        assertEquals("OWNER_FENCED", decision.reasonCode());
        assertEquals("reroute_to_active_owner", decision.nextAction());
        assertEquals(19L, decision.diagnostics().get("route_version"));
        assertEquals("gateway-owner-b", decision.diagnostics().get("active_owner"));
        assertEquals("gateway-owner-a", decision.diagnostics().get("incoming_owner"));
        assertEquals("gateway-owner-a", decision.diagnostics().get("fenced_owner"));
    }

    @Test
    void newOwnerContinuesUsingAnchorTupleAfterFenceActivation() {
        RouteOwnershipStore routeStore = fixedRouteStore(new RouteOwnershipRecord(
                "tenant-a",
                "session-a",
                31L,
                "skill-owner-a",
                "gateway-owner-b",
                "gateway-owner-a",
                Instant.parse("2026-03-04T00:00:00Z")));
        ResumeCoordinator coordinator = new ResumeCoordinator(routeStore, "gateway-owner-b");

        ResumeDecision first = coordinator.evaluate("tenant-a", "session-a", "turn-a", 8L, "gateway-owner-b");
        ResumeDecision second = coordinator.evaluate("tenant-a", "session-a", "turn-a", 9L, "gateway-owner-b");

        assertEquals(ResumeDecision.Outcome.CONTINUE, first.outcome());
        assertEquals(ResumeDecision.Outcome.CONTINUE, second.outcome());
        ResumeAnchor anchor = coordinator.lastAnchor("session-a").orElseThrow();
        assertEquals("session-a", anchor.sessionId());
        assertEquals("turn-a", anchor.turnId());
        assertEquals(9L, anchor.seq());
    }

    @Test
    void staleOwnerIsFrozenAfterFenceActivation() {
        RouteOwnershipStore routeStore = fixedRouteStore(new RouteOwnershipRecord(
                "tenant-a",
                "session-a",
                31L,
                "skill-owner-a",
                "gateway-owner-b",
                "gateway-owner-a",
                Instant.parse("2026-03-04T00:00:00Z")));
        ResumeCoordinator coordinator = new ResumeCoordinator(routeStore, "gateway-owner-b");
        coordinator.evaluate("tenant-a", "session-a", "turn-a", 8L, "gateway-owner-b");

        ResumeDecision stale = coordinator.evaluate("tenant-a", "session-a", "turn-a", 9L, "gateway-owner-a");

        assertEquals(ResumeDecision.Outcome.TERMINAL_FAILURE, stale.outcome());
        assertEquals("OWNER_FENCED", stale.reasonCode());
        assertEquals("reroute_to_active_owner", stale.nextAction());
        assertEquals(31L, stale.diagnostics().get("route_version"));
    }

    private RouteOwnershipStore fixedRouteStore(RouteOwnershipRecord record) {
        return new RouteOwnershipStore() {
            @Override
            public Optional<RouteOwnershipRecord> load(String tenantId, String sessionId) {
                return Optional.of(record);
            }

            @Override
            public RouteOwnershipRecord upsert(RouteOwnershipRecord routeRecord, Duration ttl) {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public RouteCasResult casTransfer(
                    String tenantId,
                    String sessionId,
                    long expectedRouteVersion,
                    String newSkillOwner,
                    String newGatewayOwner,
                    String fencedOwner,
                    Duration ttl) {
                throw new UnsupportedOperationException("not needed");
            }
        };
    }
}
