package com.chatcui.gateway.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.observability.BridgeMetricsRegistry;
import com.chatcui.gateway.observability.FailureClass;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import com.chatcui.gateway.relay.UnknownOwnerRecoveryWorker;
import com.chatcui.gateway.routing.RouteOwnershipRecord;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DeliveryRetryQueueTest {

    @Test
    void outageEnqueuesRetryWithoutBlockingCallerThread() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DeliveryStatusReporter reporter = new DeliveryStatusReporter();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BridgeMetricsRegistry metrics = new BridgeMetricsRegistry(meterRegistry);
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
                reporter,
                metrics);

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
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BridgeMetricsRegistry metrics = new BridgeMetricsRegistry(meterRegistry);
        AtomicInteger attempts = new AtomicInteger();
        DeliveryRetryQueue queue = new DeliveryRetryQueue(
                scheduler,
                2,
                Duration.ofMillis(5),
                event -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("still down");
                },
                reporter,
                metrics);

        SkillTurnForwardEvent event = sampleEvent();
        reporter.pending(event);
        queue.enqueue(event, new IllegalStateException("first failure"));

        assertTrue(waitUntil(() -> "failed".equals(reporter.currentStatus(event).orElse(null)), 1000));
        assertEquals(2, attempts.get());
        assertEquals(1.0, counterCount(meterRegistry, "chatcui.gateway.persistence.retry.outcomes", "gateway.persistence.retry", "failed", FailureClass.PERSISTENCE, true));
        assertTrue(waitUntil(
                () -> meterRegistry.find("chatcui.gateway.persistence.retry.duration")
                        .tags("component", "gateway.persistence.retry", "outcome", "failed", "failure_class", FailureClass.PERSISTENCE.value(), "retryable", "true")
                        .timer() != null,
                1000));
        Timer timer = meterRegistry.find("chatcui.gateway.persistence.retry.duration")
                .tags("component", "gateway.persistence.retry", "outcome", "failed", "failure_class", FailureClass.PERSISTENCE.value(), "retryable", "true")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        scheduler.shutdownNow();
    }

    @Test
    void successfulRetryMovesStatusFromPendingToSaved() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        DeliveryStatusReporter reporter = new DeliveryStatusReporter();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BridgeMetricsRegistry metrics = new BridgeMetricsRegistry(meterRegistry);
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
                reporter,
                metrics);

        SkillTurnForwardEvent event = sampleEvent();
        reporter.pending(event);
        queue.enqueue(event, new IllegalStateException("first failure"));

        assertTrue(waitUntil(() -> "saved".equals(reporter.currentStatus(event).orElse(null)), 1000));
        assertEquals(2, attempts.get());
        assertEquals(1.0, counterCount(meterRegistry, "chatcui.gateway.persistence.retry.outcomes", "gateway.persistence.retry", "pending", FailureClass.PERSISTENCE, true));
        assertEquals(1.0, counterCount(meterRegistry, "chatcui.gateway.persistence.retry.outcomes", "gateway.persistence.retry", "saved", FailureClass.PERSISTENCE, false));
        assertNull(meterRegistry.find("chatcui.gateway.persistence.retry.outcomes")
                .tags("component", "gateway.persistence.retry", "outcome", "failed", "failure_class", FailureClass.PERSISTENCE.value(), "retryable", "true")
                .counter());
        assertTrue(waitUntil(
                () -> meterRegistry.find("chatcui.gateway.persistence.retry.duration")
                        .tags("component", "gateway.persistence.retry", "outcome", "saved", "failure_class", FailureClass.PERSISTENCE.value(), "retryable", "false")
                        .timer() != null,
                1000));
        Timer timer = meterRegistry.find("chatcui.gateway.persistence.retry.duration")
                .tags("component", "gateway.persistence.retry", "outcome", "saved", "failure_class", FailureClass.PERSISTENCE.value(), "retryable", "false")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertHasStableTagKeys(meterRegistry);
        scheduler.shutdownNow();
    }

    @Test
    void reconnectAndResumeOutcomesEmitLowCardinalityMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BridgeMetricsRegistry metrics = new BridgeMetricsRegistry(meterRegistry);

        metrics.recordReconnectOutcome("resumed", FailureClass.BRIDGE, true);
        metrics.recordResumeOutcome("dropped_duplicate", FailureClass.BRIDGE, true);

        assertEquals(1.0, counterCount(meterRegistry, "chatcui.gateway.bridge.reconnect.outcomes", "gateway.bridge.reconnect", "resumed", FailureClass.BRIDGE, true));
        assertEquals(1.0, counterCount(meterRegistry, "chatcui.gateway.bridge.resume.outcomes", "gateway.bridge.resume", "dropped_duplicate", FailureClass.BRIDGE, true));
        assertHasStableTagKeys(meterRegistry);
    }

    @Test
    void unknownOwnerRecoveryRetriesWithinFifteenMinuteWindow() {
        AtomicInteger retryAttempts = new AtomicInteger();
        MutableClock clock = new MutableClock(Instant.parse("2026-03-04T00:00:00Z"));
        UnknownOwnerRecoveryWorker worker = new UnknownOwnerRecoveryWorker(
                Duration.ofMinutes(15),
                clock,
                (tenantId, sessionId) -> java.util.Optional.of(new RouteOwnershipRecord(
                        tenantId,
                        sessionId,
                        19L,
                        "skill-owner-a",
                        "gateway-owner-b",
                        null,
                        clock.instant())),
                (entry, routeRecord) -> retryAttempts.incrementAndGet());
        UnknownOwnerRecoveryWorker.RecoveryEntry entry = new UnknownOwnerRecoveryWorker.RecoveryEntry(
                "tenant-a",
                "session-a",
                "turn-a",
                4L,
                "skill.turn.delta",
                "trace-a",
                clock.instant(),
                0L);

        UnknownOwnerRecoveryWorker.RecoveryResult result = worker.process(entry);

        assertEquals(UnknownOwnerRecoveryWorker.RecoveryStatus.RETRIED, result.status());
        assertEquals(1, retryAttempts.get());
        assertEquals(19L, result.routeVersion());
        assertEquals("trace-a", result.traceId());
        assertNull(result.errorCode());
        assertNull(result.nextAction());
    }

    @Test
    void unknownOwnerRecoveryExpiresWithDeterministicTerminalEnvelope() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-04T00:16:00Z"));
        UnknownOwnerRecoveryWorker worker = new UnknownOwnerRecoveryWorker(
                Duration.ofMinutes(15),
                clock,
                (tenantId, sessionId) -> java.util.Optional.empty(),
                (entry, routeRecord) -> {
                    throw new AssertionError("retry should not run after replay-window expiry");
                });
        UnknownOwnerRecoveryWorker.RecoveryEntry entry = new UnknownOwnerRecoveryWorker.RecoveryEntry(
                "tenant-a",
                "session-a",
                "turn-a",
                4L,
                "skill.turn.delta",
                "trace-timeout",
                Instant.parse("2026-03-04T00:00:00Z"),
                31L);

        UnknownOwnerRecoveryWorker.RecoveryResult result = worker.process(entry);

        assertEquals(UnknownOwnerRecoveryWorker.RecoveryStatus.REPLAY_WINDOW_EXPIRED, result.status());
        assertEquals("ROUTE_REPLAY_WINDOW_EXPIRED", result.errorCode());
        assertEquals("restart_session", result.nextAction());
        assertEquals("trace-timeout", result.traceId());
        assertEquals(31L, result.routeVersion());
    }

    private double counterCount(
            SimpleMeterRegistry registry,
            String name,
            String component,
            String outcome,
            FailureClass failureClass,
            boolean retryable) {
        return registry.find(name)
                .tags("component", component, "outcome", outcome, "failure_class", failureClass.value(), "retryable", Boolean.toString(retryable))
                .counter()
                .count();
    }

    private void assertHasStableTagKeys(SimpleMeterRegistry registry) {
        Set<String> expected = Set.of("component", "failure_class", "outcome", "retryable");
        for (Meter meter : registry.getMeters()) {
            Set<String> actual = meter.getId().getTags().stream().map(tag -> tag.getKey()).collect(java.util.stream.Collectors.toSet());
            assertEquals(expected, actual);
        }
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

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
