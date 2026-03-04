package com.chatcui.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.observability.BridgeMetricsRegistry;
import com.chatcui.gateway.observability.FailureClass;
import com.chatcui.gateway.persistence.DeliveryRetryQueue;
import com.chatcui.gateway.persistence.DeliveryStatusReporter;
import com.chatcui.gateway.persistence.SkillPersistenceForwarder;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import com.chatcui.gateway.runtime.BridgePersistencePublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
            SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
            BridgeMetricsRegistry metricsRegistry = new BridgeMetricsRegistry(meterRegistry);
            DeliveryStatusReporter reporter = new DeliveryStatusReporter();
            List<SkillTurnForwardEvent> persisted = new CopyOnWriteArrayList<>();
            DeliveryRetryQueue retryQueue = new DeliveryRetryQueue(
                    scheduler,
                    2,
                    Duration.ofMillis(5),
                    persisted::add,
                    reporter,
                    metricsRegistry
            );
            SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(
                    payload -> {
                        throw new IllegalStateException("skill service unavailable");
                    },
                    retryQueue,
                    executor
            );
            BridgePersistencePublisher publisher = new BridgePersistencePublisher(forwarder, reporter, metricsRegistry);

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
            assertEquals(3.0, counterCount(
                    meterRegistry,
                    "chatcui.gateway.bridge.resume.outcomes",
                    "gateway.bridge.resume",
                    "continue",
                    FailureClass.BRIDGE,
                    true));
            assertEquals(3.0, counterCount(
                    meterRegistry,
                    "chatcui.gateway.bridge.reconnect.outcomes",
                    "gateway.bridge.reconnect",
                    "resumed",
                    FailureClass.BRIDGE,
                    true));
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
            SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
            BridgeMetricsRegistry metricsRegistry = new BridgeMetricsRegistry(meterRegistry);
            DeliveryStatusReporter reporter = new DeliveryStatusReporter();
            List<SkillTurnForwardEvent> persisted = new CopyOnWriteArrayList<>();
            DeliveryRetryQueue retryQueue = new DeliveryRetryQueue(
                    scheduler,
                    2,
                    Duration.ofMillis(5),
                    persisted::add,
                    reporter,
                    metricsRegistry
            );
            SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(
                    payload -> {
                        throw new IllegalStateException("skill service unavailable");
                    },
                    retryQueue,
                    executor
            );
            BridgePersistencePublisher publisher = new BridgePersistencePublisher(forwarder, reporter, metricsRegistry);

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
            assertEquals(1.0, counterCount(
                    meterRegistry,
                    "chatcui.gateway.bridge.resume.outcomes",
                    "gateway.bridge.resume",
                    "continue",
                    FailureClass.BRIDGE,
                    true));
            assertEquals(1.0, counterCount(
                    meterRegistry,
                    "chatcui.gateway.bridge.resume.outcomes",
                    "gateway.bridge.resume",
                    "compensate_gap",
                    FailureClass.BRIDGE,
                    true));
            assertEquals(2.0, counterCount(
                    meterRegistry,
                    "chatcui.gateway.bridge.reconnect.outcomes",
                    "gateway.bridge.reconnect",
                    "resumed",
                    FailureClass.BRIDGE,
                    true));
        } finally {
            executor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void ownerConflictEmitsTerminalFailureMetrics() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
            BridgeMetricsRegistry metricsRegistry = new BridgeMetricsRegistry(meterRegistry);
            DeliveryStatusReporter reporter = new DeliveryStatusReporter();
            List<SkillTurnForwardEvent> persisted = new CopyOnWriteArrayList<>();
            DeliveryRetryQueue retryQueue = new DeliveryRetryQueue(
                    scheduler,
                    2,
                    Duration.ofMillis(5),
                    persisted::add,
                    reporter,
                    metricsRegistry
            );
            SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(
                    payload -> {
                        throw new IllegalStateException("skill service unavailable");
                    },
                    retryQueue,
                    executor
            );
            BridgePersistencePublisher publisher = new BridgePersistencePublisher(forwarder, reporter, metricsRegistry);

            SkillTurnForwardEvent ownerA = event("turn-shared-terminal", 1L, "delta", "draft");
            SkillTurnForwardEvent ownerB = new SkillTurnForwardEvent(
                    ownerA.tenantId(),
                    "client-conflict",
                    ownerA.sessionId(),
                    ownerA.turnId(),
                    2L,
                    "trace-terminal",
                    ownerA.actor(),
                    ownerA.eventType(),
                    "payload-conflict",
                    ownerA.topic());

            publisher.publish(ownerA.topic(), ownerA);
            publisher.publish(ownerB.topic(), ownerB);

            assertTrue(waitUntil(() -> persisted.size() == 2, 1500));
            assertEquals(List.of("delta", "error"), persisted.stream().map(SkillTurnForwardEvent::eventType).toList());
            SkillTurnForwardEvent terminal = persisted.get(1);
            assertEquals("RESUME_OWNER_CONFLICT", terminal.reasonCode());
            assertEquals("restart_session", terminal.nextAction());
            assertEquals(1.0, counterCount(
                    meterRegistry,
                    "chatcui.gateway.bridge.resume.outcomes",
                    "gateway.bridge.resume",
                    "terminal_failure",
                    FailureClass.BRIDGE,
                    false));
            assertEquals(1.0, counterCount(
                    meterRegistry,
                    "chatcui.gateway.bridge.reconnect.outcomes",
                    "gateway.bridge.reconnect",
                    "failed",
                    FailureClass.BRIDGE,
                    false));
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

    private double counterCount(
            SimpleMeterRegistry registry,
            String name,
            String component,
            String outcome,
            FailureClass failureClass,
            boolean retryable) {
        return registry.find(name)
                .tags(
                        "component",
                        component,
                        "outcome",
                        outcome,
                        "failure_class",
                        failureClass.value(),
                        "retryable",
                        Boolean.toString(retryable))
                .counter()
                .count();
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
