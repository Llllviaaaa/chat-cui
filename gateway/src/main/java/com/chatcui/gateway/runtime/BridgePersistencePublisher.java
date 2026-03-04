package com.chatcui.gateway.runtime;

import com.chatcui.gateway.observability.BridgeMetricsRegistry;
import com.chatcui.gateway.persistence.DeliveryStatusReporter;
import com.chatcui.gateway.persistence.SkillPersistenceForwarder;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import com.chatcui.gateway.relay.DeliveryAckStateMachine;
import com.chatcui.gateway.relay.RelayEnvelope;
import com.chatcui.gateway.relay.RelayPublisher;
import com.chatcui.gateway.routing.RouteOwnershipRecord;
import com.chatcui.gateway.routing.RouteOwnershipStore;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BridgePersistencePublisher {
    private static final System.Logger LOGGER = System.getLogger(BridgePersistencePublisher.class.getName());
    private static final Set<String> PERSISTENCE_TOPICS = Set.of(
            "skill.turn.delta",
            "skill.turn.final",
            "skill.turn.completed",
            "skill.turn.error");
    private static final String COMPENSATE_TOPIC = "skill.turn.compensate";

    private final SkillPersistenceForwarder forwarder;
    private final DeliveryStatusReporter statusReporter;
    private final ResumeCoordinator resumeCoordinator;
    private final BridgeMetricsRegistry metricsRegistry;
    private final RouteOwnershipStore routeOwnershipStore;
    private final RelayPublisher relayPublisher;
    private final String gatewayOwnerId;
    private final Set<String> emittedRelayTuples = ConcurrentHashMap.newKeySet();
    private final DeliveryAckStateMachine deliveryAckStateMachine = new DeliveryAckStateMachine();

    public BridgePersistencePublisher(SkillPersistenceForwarder forwarder, DeliveryStatusReporter statusReporter) {
        this(
                forwarder,
                statusReporter,
                new ResumeCoordinator(),
                BridgeMetricsRegistry.noop(),
                null,
                null,
                null);
    }

    public BridgePersistencePublisher(
            SkillPersistenceForwarder forwarder,
            DeliveryStatusReporter statusReporter,
            BridgeMetricsRegistry metricsRegistry) {
        this(
                forwarder,
                statusReporter,
                new ResumeCoordinator(),
                metricsRegistry,
                null,
                null,
                null);
    }

    BridgePersistencePublisher(
            SkillPersistenceForwarder forwarder,
            DeliveryStatusReporter statusReporter,
            ResumeCoordinator resumeCoordinator) {
        this(
                forwarder,
                statusReporter,
                resumeCoordinator,
                BridgeMetricsRegistry.noop(),
                null,
                null,
                null);
    }

    BridgePersistencePublisher(
            SkillPersistenceForwarder forwarder,
            DeliveryStatusReporter statusReporter,
            ResumeCoordinator resumeCoordinator,
            BridgeMetricsRegistry metricsRegistry) {
        this(
                forwarder,
                statusReporter,
                resumeCoordinator,
                metricsRegistry,
                null,
                null,
                null);
    }

    BridgePersistencePublisher(
            SkillPersistenceForwarder forwarder,
            DeliveryStatusReporter statusReporter,
            ResumeCoordinator resumeCoordinator,
            BridgeMetricsRegistry metricsRegistry,
            RouteOwnershipStore routeOwnershipStore,
            RelayPublisher relayPublisher,
            String gatewayOwnerId) {
        this.forwarder = Objects.requireNonNull(forwarder, "forwarder must not be null");
        this.statusReporter = Objects.requireNonNull(statusReporter, "statusReporter must not be null");
        this.resumeCoordinator = Objects.requireNonNull(resumeCoordinator, "resumeCoordinator must not be null");
        this.metricsRegistry = metricsRegistry == null ? BridgeMetricsRegistry.noop() : metricsRegistry;
        this.routeOwnershipStore = routeOwnershipStore;
        this.relayPublisher = relayPublisher;
        this.gatewayOwnerId = normalizeOptional(gatewayOwnerId);
    }

    public void publish(String topic, SkillTurnForwardEvent event) {
        if (!PERSISTENCE_TOPICS.contains(topic)) {
            return;
        }

        ResumeDecision decision = resumeCoordinator.evaluate(
                event.sessionId(),
                event.turnId(),
                event.seq(),
                event.clientId());
        recordDecisionMetrics(decision);

        switch (decision.outcome()) {
            case CONTINUE -> forwardWithRouteAwareness(event);
            case DROP_DUPLICATE -> log("duplicate dropped", decision, event);
            case COMPENSATE_GAP -> {
                SkillTurnForwardEvent compensation = compensateEvent(event, decision);
                log("gap compensation emitted", decision, compensation);
                forwardWithRouteAwareness(compensation);
            }
            case TERMINAL_FAILURE -> {
                SkillTurnForwardEvent terminal = terminalFailureEvent(event, decision);
                log("terminal resume failure emitted", decision, terminal);
                forwardWithRouteAwareness(terminal);
            }
        }
    }

    private void recordDecisionMetrics(ResumeDecision decision) {
        boolean retryable = decision.outcome() != ResumeDecision.Outcome.TERMINAL_FAILURE;
        metricsRegistry.recordBridgeResumeOutcome(resumeOutcomeTag(decision.outcome()), retryable);
        metricsRegistry.recordBridgeReconnectOutcome(retryable ? "resumed" : "failed", retryable);
    }

    private String resumeOutcomeTag(ResumeDecision.Outcome outcome) {
        return switch (outcome) {
            case CONTINUE -> "continue";
            case DROP_DUPLICATE -> "dropped_duplicate";
            case COMPENSATE_GAP -> "compensate_gap";
            case TERMINAL_FAILURE -> "terminal_failure";
        };
    }

    private void forwardAccepted(SkillTurnForwardEvent event) {
        statusReporter.pending(event);
        forwarder.forward(event);
    }

    private void forwardWithRouteAwareness(SkillTurnForwardEvent event) {
        Optional<RouteOwnershipRecord> routeRecord = loadRouteRecord(event);
        long routeVersion = routeRecord.map(RouteOwnershipRecord::routeVersion).orElse(0L);
        deliveryAckStateMachine.markGatewayOwnerAccepted(event, routeVersion);
        if (isTerminalTimeoutEvent(event)) {
            forwardAccepted(event);
            markTimeout(event, routeVersion, event.reasonCode(), event.nextAction());
            return;
        }
        if (routeRecord.isPresent() && shouldRelay(routeRecord.get())) {
            publishFirstHopRelay(routeRecord.get(), event);
            return;
        }
        forwardAccepted(event);
        deliveryAckStateMachine.markClientDelivered(event, routeVersion);
    }

    private Optional<RouteOwnershipRecord> loadRouteRecord(SkillTurnForwardEvent event) {
        if (routeOwnershipStore == null || relayPublisher == null || gatewayOwnerId == null) {
            return Optional.empty();
        }
        return routeOwnershipStore.load(event.tenantId(), event.sessionId());
    }

    private boolean shouldRelay(RouteOwnershipRecord routeRecord) {
        return !routeRecord.gatewayOwner().equals(gatewayOwnerId);
    }

    private void publishFirstHopRelay(RouteOwnershipRecord routeRecord, SkillTurnForwardEvent event) {
        RelayEnvelope envelope = RelayEnvelope.firstHop(event, routeRecord, gatewayOwnerId);
        if (!emittedRelayTuples.add(envelope.dedupeKey())) {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "relay duplicate suppressed: dedupe_key={0}, session_id={1}, turn_id={2}, seq={3}, topic={4}",
                    envelope.dedupeKey(),
                    envelope.sessionId(),
                    envelope.turnId(),
                    envelope.seq(),
                    envelope.topic());
            deliveryAckStateMachine.markClientDelivered(event, routeRecord.routeVersion());
            return;
        }
        try {
            RelayPublisher.PublishReceipt receipt = relayPublisher.publishFirstHop(envelope);
            if (!receipt.accepted()) {
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "relay duplicate reported by publisher: dedupe_key={0}, session_id={1}, turn_id={2}, seq={3}, topic={4}",
                        envelope.dedupeKey(),
                        envelope.sessionId(),
                        envelope.turnId(),
                        envelope.seq(),
                        envelope.topic());
            }
            deliveryAckStateMachine.markClientDelivered(event, routeRecord.routeVersion());
        } catch (RuntimeException publishError) {
            markTimeout(event, routeRecord.routeVersion(), "RELAY_CLIENT_DELIVERY_TIMEOUT", "retry_via_route_recheck");
            throw publishError;
        }
    }

    Optional<DeliveryAckStateMachine.AckSnapshot> deliveryAck(SkillTurnForwardEvent event) {
        return deliveryAckStateMachine.current(event);
    }

    private SkillTurnForwardEvent compensateEvent(SkillTurnForwardEvent source, ResumeDecision decision) {
        long expectedSeq = asLong(decision.diagnostics().get("expected_seq"), source.seq());
        long incomingSeq = asLong(decision.diagnostics().get("incoming_seq"), source.seq());
        return new SkillTurnForwardEvent(
                source.tenantId(),
                source.clientId(),
                source.sessionId(),
                source.turnId(),
                expectedSeq,
                source.traceId(),
                "system",
                "compensate",
                "gap_detected expected_seq=%d incoming_seq=%d".formatted(expectedSeq, incomingSeq),
                COMPENSATE_TOPIC,
                decision.reasonCode(),
                decision.nextAction());
    }

    private SkillTurnForwardEvent terminalFailureEvent(SkillTurnForwardEvent source, ResumeDecision decision) {
        return new SkillTurnForwardEvent(
                source.tenantId(),
                source.clientId(),
                source.sessionId(),
                source.turnId(),
                source.seq(),
                source.traceId(),
                "system",
                "error",
                "resume_terminal_failure",
                "skill.turn.error",
                decision.reasonCode(),
                decision.nextAction());
    }

    private void log(String action, ResumeDecision decision, SkillTurnForwardEvent event) {
        LOGGER.log(
                System.Logger.Level.INFO,
                "resume {0}: outcome={1}, reason_code={2}, next_action={3}, session_id={4}, turn_id={5}, seq={6}, diagnostics={7}",
                action,
                decision.outcome().name().toLowerCase(Locale.ROOT),
                decision.reasonCode(),
                decision.nextAction(),
                event.sessionId(),
                event.turnId(),
                event.seq(),
                decision.diagnostics());
    }

    private long asLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isTerminalTimeoutEvent(SkillTurnForwardEvent event) {
        return "skill.turn.error".equals(event.topic()) && event.reasonCode() != null && !event.reasonCode().isBlank();
    }

    private void markTimeout(SkillTurnForwardEvent event, long routeVersion, String errorCode, String nextAction) {
        String normalizedErrorCode = normalizeRequired(errorCode, "error_code");
        String normalizedNextAction = normalizeRequired(nextAction, "next_action");
        deliveryAckStateMachine.markClientDeliveryTimeout(event, routeVersion, normalizedErrorCode, normalizedNextAction);
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
