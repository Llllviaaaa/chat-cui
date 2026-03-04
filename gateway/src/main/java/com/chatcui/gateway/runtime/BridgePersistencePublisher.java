package com.chatcui.gateway.runtime;

import com.chatcui.gateway.observability.BridgeMetricsRegistry;
import com.chatcui.gateway.persistence.DeliveryStatusReporter;
import com.chatcui.gateway.persistence.SkillPersistenceForwarder;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import java.util.Locale;
import java.util.Set;

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

    public BridgePersistencePublisher(SkillPersistenceForwarder forwarder, DeliveryStatusReporter statusReporter) {
        this(forwarder, statusReporter, new ResumeCoordinator(), BridgeMetricsRegistry.noop());
    }

    public BridgePersistencePublisher(
            SkillPersistenceForwarder forwarder,
            DeliveryStatusReporter statusReporter,
            BridgeMetricsRegistry metricsRegistry) {
        this(forwarder, statusReporter, new ResumeCoordinator(), metricsRegistry);
    }

    BridgePersistencePublisher(
            SkillPersistenceForwarder forwarder,
            DeliveryStatusReporter statusReporter,
            ResumeCoordinator resumeCoordinator) {
        this(forwarder, statusReporter, resumeCoordinator, BridgeMetricsRegistry.noop());
    }

    BridgePersistencePublisher(
            SkillPersistenceForwarder forwarder,
            DeliveryStatusReporter statusReporter,
            ResumeCoordinator resumeCoordinator,
            BridgeMetricsRegistry metricsRegistry) {
        this.forwarder = forwarder;
        this.statusReporter = statusReporter;
        this.resumeCoordinator = resumeCoordinator;
        this.metricsRegistry = metricsRegistry == null ? BridgeMetricsRegistry.noop() : metricsRegistry;
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
            case CONTINUE -> forwardAccepted(event);
            case DROP_DUPLICATE -> log("duplicate dropped", decision, event);
            case COMPENSATE_GAP -> {
                SkillTurnForwardEvent compensation = compensateEvent(event, decision);
                log("gap compensation emitted", decision, compensation);
                forwardAccepted(compensation);
            }
            case TERMINAL_FAILURE -> {
                SkillTurnForwardEvent terminal = terminalFailureEvent(event, decision);
                log("terminal resume failure emitted", decision, terminal);
                forwardAccepted(terminal);
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
}
