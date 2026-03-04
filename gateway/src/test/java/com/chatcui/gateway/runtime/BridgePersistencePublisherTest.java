package com.chatcui.gateway.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.persistence.DeliveryStatusReporter;
import com.chatcui.gateway.persistence.SkillPersistenceForwarder;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BridgePersistencePublisherTest {

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

    private SkillTurnForwardEvent sampleEvent(String topic, long seq) {
        return new SkillTurnForwardEvent(
                "tenant-a",
                "client-a",
                "session-a",
                "turn-" + seq,
                seq,
                "trace-a",
                "assistant",
                "delta",
                "payload-" + seq,
                topic);
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
