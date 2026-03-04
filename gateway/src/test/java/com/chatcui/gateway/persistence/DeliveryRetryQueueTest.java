package com.chatcui.gateway.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DeliveryRetryQueueTest {

    @Test
    void outageEnqueuesRetryWithoutBlockingCallerThread() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DeliveryStatusReporter reporter = new DeliveryStatusReporter();
        AtomicInteger attempts = new AtomicInteger();
        DeliveryRetryQueue queue = new DeliveryRetryQueue(
                scheduler,
                2,
                Duration.ofMillis(10),
                event -> {
                    attempts.incrementAndGet();
                    Thread.sleep(150);
                    throw new IllegalStateException("down");
                },
                reporter);

        long started = System.nanoTime();
        queue.enqueue(sampleEvent(), new IllegalStateException("first failure"));
        Duration duration = Duration.ofNanos(System.nanoTime() - started);

        assertTrue(duration.toMillis() < 100);
        assertTrue(waitUntil(() -> attempts.get() > 0, 1000));
        scheduler.shutdownNow();
    }

    @Test
    void retriesAreBoundedAndFinalStateMarkedFailed() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DeliveryStatusReporter reporter = new DeliveryStatusReporter();
        AtomicInteger attempts = new AtomicInteger();
        DeliveryRetryQueue queue = new DeliveryRetryQueue(
                scheduler,
                2,
                Duration.ofMillis(5),
                event -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("still down");
                },
                reporter);

        SkillTurnForwardEvent event = sampleEvent();
        reporter.pending(event);
        queue.enqueue(event, new IllegalStateException("first failure"));

        assertTrue(waitUntil(() -> "failed".equals(reporter.currentStatus(event).orElse(null)), 1000));
        assertEquals(2, attempts.get());
        scheduler.shutdownNow();
    }

    @Test
    void successfulRetryMovesStatusFromPendingToSaved() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DeliveryStatusReporter reporter = new DeliveryStatusReporter();
        AtomicInteger attempts = new AtomicInteger();
        DeliveryRetryQueue queue = new DeliveryRetryQueue(
                scheduler,
                3,
                Duration.ofMillis(5),
                event -> {
                    int current = attempts.incrementAndGet();
                    if (current == 1) {
                        throw new IllegalStateException("temporary down");
                    }
                },
                reporter);

        SkillTurnForwardEvent event = sampleEvent();
        reporter.pending(event);
        queue.enqueue(event, new IllegalStateException("first failure"));

        assertTrue(waitUntil(() -> "saved".equals(reporter.currentStatus(event).orElse(null)), 1000));
        assertEquals(2, attempts.get());
        scheduler.shutdownNow();
    }

    private SkillTurnForwardEvent sampleEvent() {
        return new SkillTurnForwardEvent(
                "tenant-a",
                "client-a",
                "session-a",
                "turn-a",
                7L,
                "trace-a",
                "assistant",
                "delta",
                "payload",
                "skill.turn.delta");
    }

    private boolean waitUntil(Check check, long timeoutMs) throws InterruptedException {
        long started = System.currentTimeMillis();
        while (System.currentTimeMillis() - started < timeoutMs) {
            if (check.evaluate()) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    @FunctionalInterface
    private interface Check {
        boolean evaluate();
    }
}
