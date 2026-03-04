package com.chatcui.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.persistence.DeliveryRetryQueue;
import com.chatcui.gateway.persistence.DeliveryStatusReporter;
import com.chatcui.gateway.persistence.SkillPersistenceForwarder;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import com.chatcui.gateway.runtime.BridgePersistencePublisher;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SkillPersistenceForwardingIntegrationTest {

    @Test
    void forwardedDeltaFinalCompletedEventsPersistWithExpectedDeliveryTransitions() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            DeliveryStatusReporter reporter = new DeliveryStatusReporter();
            List<SkillTurnForwardEvent> persisted = new CopyOnWriteArrayList<>();
            DeliveryRetryQueue retryQueue = new DeliveryRetryQueue(
                    scheduler,
                    2,
                    Duration.ofMillis(5),
                    persisted::add,
                    reporter
            );
            SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(
                    payload -> {
                        throw new IllegalStateException("skill service unavailable");
                    },
                    retryQueue,
                    executor
            );
            BridgePersistencePublisher publisher = new BridgePersistencePublisher(forwarder, reporter);

            SkillTurnForwardEvent delta = event("turn-shared-001", 1L, "delta", "draft");
            SkillTurnForwardEvent fin = event("turn-shared-001", 2L, "final", "answer-final");
            SkillTurnForwardEvent completed = event("turn-shared-001", 3L, "completed", "answer-final");

            publisher.publish(delta.topic(), delta);
            publisher.publish(fin.topic(), fin);
            publisher.publish(completed.topic(), completed);

            assertTrue(waitUntil(() -> persisted.size() == 3, 1500));
            assertTrue(waitUntil(() -> "saved".equals(reporter.currentStatus(delta).orElse(null)), 1000));
            assertTrue(waitUntil(() -> "saved".equals(reporter.currentStatus(fin).orElse(null)), 1000));
            assertTrue(waitUntil(() -> "saved".equals(reporter.currentStatus(completed).orElse(null)), 1000));
            assertEquals(List.of(1L, 2L, 3L), persisted.stream().map(SkillTurnForwardEvent::seq).toList());
            assertEquals(List.of("delta", "final", "completed"), persisted.stream().map(SkillTurnForwardEvent::eventType).toList());
            assertEquals("saved", reporter.currentStatus(delta).orElseThrow());
            assertEquals("saved", reporter.currentStatus(fin).orElseThrow());
            assertEquals("saved", reporter.currentStatus(completed).orElseThrow());
        } finally {
            executor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void gapCompensationIsPersistedAndOutOfOrderTupleIsBlocked() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            DeliveryStatusReporter reporter = new DeliveryStatusReporter();
            List<SkillTurnForwardEvent> persisted = new CopyOnWriteArrayList<>();
            DeliveryRetryQueue retryQueue = new DeliveryRetryQueue(
                    scheduler,
                    2,
                    Duration.ofMillis(5),
                    persisted::add,
                    reporter
            );
            SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(
                    payload -> {
                        throw new IllegalStateException("skill service unavailable");
                    },
                    retryQueue,
                    executor
            );
            BridgePersistencePublisher publisher = new BridgePersistencePublisher(forwarder, reporter);

            SkillTurnForwardEvent first = event("turn-shared-gap", 1L, "delta", "draft");
            SkillTurnForwardEvent gap = event("turn-shared-gap", 3L, "delta", "late-delta");
            publisher.publish(first.topic(), first);
            publisher.publish(gap.topic(), gap);

            assertTrue(waitUntil(() -> persisted.size() == 2, 1500));
            assertEquals(List.of(1L, 2L), persisted.stream().map(SkillTurnForwardEvent::seq).toList());
            assertEquals(List.of("delta", "compensate"), persisted.stream().map(SkillTurnForwardEvent::eventType).toList());
            SkillTurnForwardEvent compensate = persisted.get(1);
            assertEquals("SEQ_GAP_COMPENSATION_REQUIRED", compensate.reasonCode());
            assertEquals("compensate_and_resume", compensate.nextAction());
        } finally {
            executor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    private SkillTurnForwardEvent event(String turnId, long seq, String eventType, String payload) {
        return new SkillTurnForwardEvent(
                "tenant-shared",
                "client-shared",
                "session-shared-001",
                turnId,
                seq,
                "trace-" + seq,
                "assistant",
                eventType,
                payload,
                "skill.turn." + eventType
        );
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
