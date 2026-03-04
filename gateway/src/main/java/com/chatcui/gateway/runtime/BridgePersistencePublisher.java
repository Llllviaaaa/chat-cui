package com.chatcui.gateway.runtime;

import com.chatcui.gateway.observability.BridgeMetricsRegistry;
import com.chatcui.gateway.observability.FailureClass;
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

    public BridgePersistencePublisher(
            SkillPersistenceForwarder forwarder,
            DeliveryStatusReporter statusReporter,
            BridgeMetricsRegistry metricsRegistry,
            RouteOwnershipStore routeOwnershipStore,
            RelayPublisher relayPublisher,
            String gatewayOwnerId) {
        this(
                forwarder,
                statusReporter,
                new ResumeCoordinator(),
                metricsRegistry,
                routeOwnershipStore,
                relayPublisher,
                gatewayOwnerId);
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
                event.tenantId(),
                event.sessionId(),
                event.turnId(),
                event.seq(),
                resolveResumeOwner(event));
        recordDecisionMetrics(decision);
        long routeVersion = routeVersionForDecision(decision);

        switch (decision.outcome()) {
            case CONTINUE -> forwardWithRouteAwareness(event);
            case DROP_DUPLICATE -> logResume("duplicate dropped", decision, event, routeVersion);
            case COMPENSATE_GAP -> {
                SkillTurnForwardEvent compensation = compensateEvent(event, decision);
                logResume("gap compensation emitted", decision, compensation, routeVersion);
                forwardWithRouteAwareness(compensation);
            }
            case TERMINAL_FAILURE -> {
                SkillTurnForwardEvent terminal = terminalFailureEvent(event, decision);
                logResume("terminal resume failure emitted", decision, terminal, routeVersion);
                forwardWithRouteAwareness(terminal);
            }
        }
    }

    private void recordDecisionMetrics(ResumeDecision decision) {
        boolean retryable = decision.outcome() != ResumeDecision.Outcome.TERMINAL_FAILURE;
        metricsRegistry.recordBridgeResumeOutcome(resumeOutcomeTag(decision.outcome()), retryable);
        metricsRegistry.recordBridgeReconnectOutcome(retryable ? "resumed" : "failed", retryable);
        if (decision.outcome() == ResumeDecision.Outcome.TERMINAL_FAILURE) {
            metricsRegistry.recordRouteOutcome(routeOutcomeTag(decision.reasonCode()), FailureClass.BRIDGE, false);
        }
    }

    private String resumeOutcomeTag(ResumeDecision.Outcome outcome) {
        return switch (outcome) {
            case CONTINUE -> "continue";
            case DROP_DUPLICATE -> "dropped_duplicate";
            case COMPENSATE_GAP -> "compensate_gap";
            case TERMINAL_FAILURE -> "terminal_failure";
        };
    }

    private String routeOutcomeTag(String reasonCode) {
        if ("RESUME_OWNER_CONFLICT".equals(reasonCode)) {
            return "route_conflict";
        }
        if ("OWNER_FENCED".equals(reasonCode)) {
            return "owner_fenced";
        }
        return "terminal_failure";
    }

    private long routeVersionForDecision(ResumeDecision decision) {
        return asLong(decision.diagnostics().get("route_version"), 0L);
    }

    private void forwardAccepted(SkillTurnForwardEvent event) {
        statusReporter.pending(event);
        forwarder.forward(event);
    }

    private void forwardWithRouteAwareness(SkillTurnForwardEvent event) {
        Optional<RouteOwnershipRecord> routeRecord = loadRouteRecord(event);
        long routeVersion = routeRecord.map(RouteOwnershipRecord::routeVersion).orElse(0L);
        deliveryAckStateMachine.markGatewayOwnerAccepted(event, routeVersion);
        metricsRegistry.recordAckOutcome("gateway_owner_accepted", FailureClass.BRIDGE, true);
        logDeliveryStage("gateway_owner_accepted", "accepted", event, routeVersion, null, null);
        if (routeRecord.isPresent() && isLocalOwnerFenced(routeRecord.get())) {
            metricsRegistry.recordRouteOutcome("owner_fenced", FailureClass.BRIDGE, false);
            markTimeout(event, routeVersion, "OWNER_FENCED", "reroute_to_active_owner");
            return;
        }
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
        markDelivered(event, routeVersion, "local_forward");
    }

    private Optional<RouteOwnershipRecord> loadRouteRecord(SkillTurnForwardEvent event) {
        if (routeOwnershipStore == null) {
            return Optional.empty();
        }
        return routeOwnershipStore.load(event.tenantId(), event.sessionId());
    }

    private boolean shouldRelay(RouteOwnershipRecord routeRecord) {
        return relayPublisher != null && gatewayOwnerId != null && !routeRecord.gatewayOwner().equals(gatewayOwnerId);
    }

    private boolean isLocalOwnerFenced(RouteOwnershipRecord routeRecord) {
        if (gatewayOwnerId == null) {
            return false;
        }
        String fencedOwner = normalizeOptional(routeRecord.fencedOwner());
        return fencedOwner != null && fencedOwner.equals(gatewayOwnerId);
    }

    private void publishFirstHopRelay(RouteOwnershipRecord routeRecord, SkillTurnForwardEvent event) {
        RelayEnvelope envelope = RelayEnvelope.firstHop(event, routeRecord, gatewayOwnerId);
        if (!emittedRelayTuples.add(envelope.dedupeKey())) {
            metricsRegistry.recordRelayOutcome("relay_success", FailureClass.BRIDGE, false);
            logDeliveryStage(
                    "relay_publish_duplicate_suppressed",
                    "duplicate_suppressed",
                    event,
                    routeRecord.routeVersion(),
                    null,
                    null);
            markDelivered(event, routeRecord.routeVersion(), "relay_publish_duplicate_suppressed");
            return;
        }
        try {
            RelayPublisher.PublishReceipt receipt = relayPublisher.publishFirstHop(envelope);
            if (!receipt.accepted()) {
                logDeliveryStage(
                        "relay_publish_duplicate_reported",
                        "duplicate_reported",
                        event,
                        routeRecord.routeVersion(),
                        null,
                        null);
            }
            metricsRegistry.recordRelayOutcome("relay_success", FailureClass.BRIDGE, false);
            markDelivered(event, routeRecord.routeVersion(), "relay_publish_accepted");
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

    private void logResume(String action, ResumeDecision decision, SkillTurnForwardEvent event, long routeVersion) {
        LOGGER.log(
                System.Logger.Level.INFO,
                "resume stage=resume_decision, action={0}, outcome={1}, reason_code={2}, next_action={3}, trace_id={4}, route_version={5}, session_id={6}, turn_id={7}, seq={8}, diagnostics={9}",
                action,
                decision.outcome().name().toLowerCase(Locale.ROOT),
                decision.reasonCode(),
                decision.nextAction(),
                event.traceId(),
                routeVersion,
                event.sessionId(),
                event.turnId(),
                event.seq(),
                decision.diagnostics());
    }

    private void logDeliveryStage(
            String stage,
            String outcome,
            SkillTurnForwardEvent event,
            long routeVersion,
            String reasonCode,
            String nextAction) {
        LOGGER.log(
                System.Logger.Level.INFO,
                "delivery stage={0}, outcome={1}, trace_id={2}, route_version={3}, session_id={4}, turn_id={5}, seq={6}, topic={7}, reason_code={8}, next_action={9}",
                stage,
                outcome,
                event.traceId(),
                routeVersion,
                event.sessionId(),
                event.turnId(),
                event.seq(),
                event.topic(),
                normalizeOptional(reasonCode),
                normalizeOptional(nextAction));
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

    private void markDelivered(SkillTurnForwardEvent event, long routeVersion, String stage) {
        deliveryAckStateMachine.markClientDelivered(event, routeVersion);
        metricsRegistry.recordAckOutcome("client_delivered", FailureClass.BRIDGE, false);
        logDeliveryStage(stage, "client_delivered", event, routeVersion, null, null);
    }

    private void markTimeout(SkillTurnForwardEvent event, long routeVersion, String errorCode, String nextAction) {
        String normalizedErrorCode = normalizeRequired(errorCode, "error_code");
        String normalizedNextAction = normalizeRequired(nextAction, "next_action");
        deliveryAckStateMachine.markClientDeliveryTimeout(event, routeVersion, normalizedErrorCode, normalizedNextAction);
        if (isRelayTimeoutReason(normalizedErrorCode)) {
            metricsRegistry.recordRelayOutcome("relay_timeout", FailureClass.BRIDGE, true);
        }
        metricsRegistry.recordAckOutcome("client_delivery_timeout", FailureClass.BRIDGE, true);
        logDeliveryStage(
                "client_delivery_timeout",
                "client_delivery_timeout",
                event,
                routeVersion,
                normalizedErrorCode,
                normalizedNextAction);
    }

    private boolean isRelayTimeoutReason(String errorCode) {
        return "RELAY_CLIENT_DELIVERY_TIMEOUT".equals(errorCode);
    }

    private String resolveResumeOwner(SkillTurnForwardEvent event) {
        if (gatewayOwnerId != null) {
            return gatewayOwnerId;
        }
        return event.clientId();
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
