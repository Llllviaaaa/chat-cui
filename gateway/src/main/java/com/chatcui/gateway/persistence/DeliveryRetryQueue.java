package com.chatcui.gateway.persistence;

import com.chatcui.gateway.observability.BridgeMetricsRegistry;
import com.chatcui.gateway.observability.FailureClass;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DeliveryRetryQueue implements SkillPersistenceForwarder.RetryQueue {
    private final ScheduledExecutorService scheduler;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final DeliveryAttemptSender attemptSender;
    private final DeliveryStatusReporter statusReporter;
    private final BridgeMetricsRegistry metricsRegistry;
    private final Map<String, Long> pendingSinceNanos = new ConcurrentHashMap<>();

    public DeliveryRetryQueue(
            ScheduledExecutorService scheduler,
            int maxAttempts,
            Duration retryBackoff,
            DeliveryAttemptSender attemptSender,
            DeliveryStatusReporter statusReporter) {
        this(scheduler, maxAttempts, retryBackoff, attemptSender, statusReporter, BridgeMetricsRegistry.noop());
    }

    public DeliveryRetryQueue(
            ScheduledExecutorService scheduler,
            int maxAttempts,
            Duration retryBackoff,
            DeliveryAttemptSender attemptSender,
            DeliveryStatusReporter statusReporter,
            BridgeMetricsRegistry metricsRegistry) {
        this.scheduler = scheduler;
        this.maxAttempts = maxAttempts;
        this.retryBackoff = retryBackoff;
        this.attemptSender = attemptSender;
        this.statusReporter = statusReporter;
        this.metricsRegistry = metricsRegistry == null ? BridgeMetricsRegistry.noop() : metricsRegistry;
    }

    @Override
    public void enqueue(SkillTurnForwardEvent event, Exception error) {
        statusReporter.pending(event);
        metricsRegistry.recordPersistenceOutcome("pending", FailureClass.PERSISTENCE, true);
        pendingSinceNanos.put(tupleKey(event), System.nanoTime());
        scheduleAttempt(event, 1);
    }

    private void scheduleAttempt(SkillTurnForwardEvent event, int attempt) {
        long delay = attempt == 1 ? 0L : retryBackoff.toMillis() * (attempt - 1L);
        scheduler.schedule(() -> attemptDelivery(event, attempt), delay, TimeUnit.MILLISECONDS);
    }

    private void attemptDelivery(SkillTurnForwardEvent event, int attempt) {
        try {
            attemptSender.send(event);
            statusReporter.saved(event);
            metricsRegistry.recordPersistenceOutcome("saved", FailureClass.PERSISTENCE, false);
            recordTerminalDuration(event, "saved", false);
        } catch (Exception sendError) {
            if (attempt >= maxAttempts) {
                statusReporter.failed(event, sendError);
                metricsRegistry.recordPersistenceOutcome("failed", FailureClass.PERSISTENCE, true);
                recordTerminalDuration(event, "failed", true);
                return;
            }
            scheduleAttempt(event, attempt + 1);
        }
    }

    private void recordTerminalDuration(SkillTurnForwardEvent event, String outcome, boolean retryable) {
        Long started = pendingSinceNanos.remove(tupleKey(event));
        if (started == null) {
            return;
        }
        metricsRegistry.recordPersistenceDuration(
                outcome,
                FailureClass.PERSISTENCE,
                retryable,
                System.nanoTime() - started);
    }

    private String tupleKey(SkillTurnForwardEvent event) {
        return event.sessionId() + "|" + event.turnId() + "|" + event.seq() + "|" + event.topic();
    }

    @FunctionalInterface
    public interface DeliveryAttemptSender {
        void send(SkillTurnForwardEvent event) throws Exception;
    }
}
