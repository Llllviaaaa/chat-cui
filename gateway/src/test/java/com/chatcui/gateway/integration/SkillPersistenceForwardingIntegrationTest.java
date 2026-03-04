package com.chatcui.gateway.integration;

import com.chatcui.gateway.persistence.DeliveryRetryQueue;
import com.chatcui.gateway.persistence.DeliveryStatusReporter;
import com.chatcui.gateway.persistence.SkillPersistenceForwarder;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import com.chatcui.gateway.runtime.BridgePersistencePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPersistenceForwardingIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void forwardedDeltaFinalCompletedEventsPersistWithExpectedDeliveryTransitions() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            DeliveryStatusReporter reporter = new DeliveryStatusReporter();
            List<SkillTurnForwardEvent> forwarded = new CopyOnWriteArrayList<>();
            DeliveryRetryQueue retryQueue = new DeliveryRetryQueue(
                    scheduler,
                    2,
                    Duration.ofMillis(5),
                    event -> {
                    },
                    reporter
            );
            SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(payload -> {
                SkillTurnForwardEvent event = objectMapper.readValue(payload, SkillTurnForwardEvent.class);
                forwarded.add(event);
            }, retryQueue, Executors.newSingleThreadExecutor());
            BridgePersistencePublisher publisher = new BridgePersistencePublisher(forwarder, reporter);

            SkillTurnForwardEvent delta = event("turn-shared-001", 1L, "delta", "draft");
            SkillTurnForwardEvent fin = event("turn-shared-001", 2L, "final", "answer-final");
            SkillTurnForwardEvent completed = event("turn-shared-001", 3L, "completed", "answer-final");

            publisher.publish(delta.topic(), delta);
            publisher.publish(fin.topic(), fin);
            publisher.publish(completed.topic(), completed);

            assertTrue(waitUntil(() -> forwarded.size() == 3, 1500));
            assertTrue(waitUntil(() -> "saved".equals(reporter.currentStatus(delta).orElse(null)), 1000));
            assertTrue(waitUntil(() -> "saved".equals(reporter.currentStatus(fin).orElse(null)), 1000));
            assertTrue(waitUntil(() -> "saved".equals(reporter.currentStatus(completed).orElse(null)), 1000));
            assertEquals(List.of(1L, 2L, 3L), forwarded.stream().map(SkillTurnForwardEvent::seq).toList());
            assertEquals(List.of("delta", "final", "completed"), forwarded.stream().map(SkillTurnForwardEvent::eventType).toList());
            assertEquals("saved", reporter.currentStatus(delta).orElseThrow());
            assertEquals("saved", reporter.currentStatus(fin).orElseThrow());
            assertEquals("saved", reporter.currentStatus(completed).orElseThrow());
        } finally {
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
