package com.chatcui.gateway.persistence;

import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DeliveryRetryQueue implements SkillPersistenceForwarder.RetryQueue {
    private final ScheduledExecutorService scheduler;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final DeliveryAttemptSender attemptSender;
    private final DeliveryStatusReporter statusReporter;

    public DeliveryRetryQueue(
            ScheduledExecutorService scheduler,
            int maxAttempts,
            Duration retryBackoff,
            DeliveryAttemptSender attemptSender,
            DeliveryStatusReporter statusReporter) {
        this.scheduler = scheduler;
        this.maxAttempts = maxAttempts;
        this.retryBackoff = retryBackoff;
        this.attemptSender = attemptSender;
        this.statusReporter = statusReporter;
    }

    @Override
    public void enqueue(SkillTurnForwardEvent event, Exception error) {
        statusReporter.pending(event);
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
        } catch (Exception sendError) {
            if (attempt >= maxAttempts) {
                statusReporter.failed(event, sendError);
                return;
            }
            scheduleAttempt(event, attempt + 1);
        }
    }

    @FunctionalInterface
    public interface DeliveryAttemptSender {
        void send(SkillTurnForwardEvent event) throws Exception;
    }
}
