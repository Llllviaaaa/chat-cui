package com.chatcui.gateway.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.observability.BridgeMetricsRegistry;
import com.chatcui.gateway.observability.FailureClass;
import com.chatcui.gateway.persistence.DeliveryStatusReporter;
import com.chatcui.gateway.persistence.SkillPersistenceForwarder;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BridgePersistencePublisherTest {
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    void persistenceTopicsAreForwarded() {
        AtomicInteger forwarded = new AtomicInteger();
        SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(
                payload -> forwarded.incrementAndGet(),
                (event, error) -> {
                },
                Runnable::run);
        BridgePersistencePublisher publisher = new BridgePersistencePublisher(forwarder, new DeliveryStatusReporter());

        publisher.publish("skill.turn.delta", sampleEvent("skill.turn.delta", 1L));
        publisher.publish("skill.turn.final", sampleEvent("skill.turn.final", 2L));
        publisher.publish("skill.turn.completed", sampleEvent("skill.turn.completed", 3L));
        publisher.publish("skill.turn.error", sampleEvent("skill.turn.error", 4L));

        assertEquals(4, forwarded.get());
    }

    @Test
    void nonPersistenceTopicsAreIgnored() {
        AtomicInteger forwarded = new AtomicInteger();
        AtomicInteger retries = new AtomicInteger();
        SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(
                payload -> forwarded.incrementAndGet(),
                (event, error) -> retries.incrementAndGet(),
                Runnable::run);
        BridgePersistencePublisher publisher = new BridgePersistencePublisher(forwarder, new DeliveryStatusReporter());

        publisher.publish("skill.session.started", sampleEvent("skill.session.started", 1L));
        publisher.publish("skill.ping", sampleEvent("skill.ping", 2L));

        assertEquals(0, forwarded.get());
        assertEquals(0, retries.get());
    }

    @Test
    void pendingStatusIsPublishedBeforeForwardCompletes() throws Exception {
        CountDownLatch sendGate = new CountDownLatch(1);
        AtomicInteger forwarded = new AtomicInteger();
        DeliveryStatusReporter reporter = new DeliveryStatusReporter();
        SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(
                payload -> {
                    sendGate.await(1, TimeUnit.SECONDS);
                    forwarded.incrementAndGet();
                },
                (event, error) -> {
                },
                Executors.newSingleThreadExecutor());
        BridgePersistencePublisher publisher = new BridgePersistencePublisher(forwarder, reporter);

        SkillTurnForwardEvent event = sampleEvent("skill.turn.delta", 1L);
        publisher.publish("skill.turn.delta", event);

        assertEquals("pending", reporter.currentStatus(event).orElse(null));
        sendGate.countDown();
        assertTrue(waitUntil(() -> forwarded.get() == 1, 1000));
    }

    @Test
    void gapEventsTriggerCompensationAndBlockOutOfOrderTuple() throws Exception {
        List<SkillTurnForwardEvent> persisted = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(
                payload -> {
                    persisted.add(fromPayload(payload));
                    latch.countDown();
                },
                (event, error) -> {
                },
                Runnable::run);
        BridgePersistencePublisher publisher = new BridgePersistencePublisher(forwarder, new DeliveryStatusReporter());

        SkillTurnForwardEvent first = sampleEvent("skill.turn.delta", 1L, "client-a");
        SkillTurnForwardEvent gap = sampleEvent("skill.turn.delta", 3L, "client-a");
        publisher.publish(first.topic(), first);
        publisher.publish(gap.topic(), gap);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(List.of(1L, 2L), persisted.stream().map(SkillTurnForwardEvent::seq).toList());
        assertEquals(List.of("delta", "compensate"), persisted.stream().map(SkillTurnForwardEvent::eventType).toList());
        SkillTurnForwardEvent compensation = persisted.get(1);
        assertEquals("skill.turn.compensate", compensation.topic());
        assertEquals("SEQ_GAP_COMPENSATION_REQUIRED", compensation.reasonCode());
        assertEquals("compensate_and_resume", compensation.nextAction());
    }

    @Test
    void ownerConflictEmitsDeterministicTerminalFailureEnvelope() throws Exception {
        List<SkillTurnForwardEvent> persisted = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(
                payload -> {
                    persisted.add(fromPayload(payload));
                    latch.countDown();
                },
                (event, error) -> {
                },
                Runnable::run);
        BridgePersistencePublisher publisher = new BridgePersistencePublisher(forwarder, new DeliveryStatusReporter());

        SkillTurnForwardEvent ownerA = sampleEvent("skill.turn.delta", 1L, "client-a");
        SkillTurnForwardEvent ownerB = sampleEvent("skill.turn.delta", 2L, "client-b");
        publisher.publish(ownerA.topic(), ownerA);
        publisher.publish(ownerB.topic(), ownerB);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(List.of("delta", "error"), persisted.stream().map(SkillTurnForwardEvent::eventType).toList());
        SkillTurnForwardEvent terminal = persisted.get(1);
        assertEquals("skill.turn.error", terminal.topic());
        assertEquals("RESUME_OWNER_CONFLICT", terminal.reasonCode());
        assertEquals("restart_session", terminal.nextAction());
    }

    @Test
    void resumeOutcomesEmitReconnectAndResumeCountersWithStableTags() throws Exception {
        List<SkillTurnForwardEvent> persisted = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BridgeMetricsRegistry metricsRegistry = new BridgeMetricsRegistry(meterRegistry);
        SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(
                payload -> {
                    persisted.add(fromPayload(payload));
                    latch.countDown();
                },
                (event, error) -> {
                },
                Runnable::run);
        BridgePersistencePublisher publisher =
                new BridgePersistencePublisher(forwarder, new DeliveryStatusReporter(), metricsRegistry);

        publisher.publish("skill.turn.delta", sampleEvent("skill.turn.delta", 1L, "client-a"));
        publisher.publish("skill.turn.delta", sampleEvent("skill.turn.delta", 1L, "client-a"));
        publisher.publish("skill.turn.delta", sampleEvent("skill.turn.delta", 3L, "client-a"));
        publisher.publish("skill.turn.delta", sampleEvent("skill.turn.delta", 4L, "client-b"));

        assertTrue(latch.await(1, TimeUnit.SECONDS));
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
                "dropped_duplicate",
                FailureClass.BRIDGE,
                true));
        assertEquals(1.0, counterCount(
                meterRegistry,
                "chatcui.gateway.bridge.resume.outcomes",
                "gateway.bridge.resume",
                "compensate_gap",
                FailureClass.BRIDGE,
                true));
        assertEquals(1.0, counterCount(
                meterRegistry,
                "chatcui.gateway.bridge.resume.outcomes",
                "gateway.bridge.resume",
                "terminal_failure",
                FailureClass.BRIDGE,
                false));
        assertEquals(3.0, counterCount(
                meterRegistry,
                "chatcui.gateway.bridge.reconnect.outcomes",
                "gateway.bridge.reconnect",
                "resumed",
                FailureClass.BRIDGE,
                true));
        assertEquals(1.0, counterCount(
                meterRegistry,
                "chatcui.gateway.bridge.reconnect.outcomes",
                "gateway.bridge.reconnect",
                "failed",
                FailureClass.BRIDGE,
                false));
        assertHasStableTagKeys(meterRegistry);
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

    private void assertHasStableTagKeys(SimpleMeterRegistry registry) {
        Set<String> expected = Set.of("component", "failure_class", "outcome", "retryable");
        for (Meter meter : registry.getMeters()) {
            Set<String> actual =
                    meter.getId().getTags().stream().map(tag -> tag.getKey()).collect(java.util.stream.Collectors.toSet());
            assertEquals(expected, actual);
        }
    }

    private SkillTurnForwardEvent sampleEvent(String topic, long seq, String clientId) {
        return new SkillTurnForwardEvent(
                "tenant-a",
                clientId,
                "session-a",
                "turn-shared",
                seq,
                "trace-a",
                "assistant",
                "delta",
                "payload-" + seq,
                topic);
    }

    private SkillTurnForwardEvent sampleEvent(String topic, long seq) {
        return sampleEvent(topic, seq, "client-a");
    }

    private SkillTurnForwardEvent fromPayload(byte[] payload) throws Exception {
        return objectMapper.readValue(payload, new TypeReference<>() {
        });
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
