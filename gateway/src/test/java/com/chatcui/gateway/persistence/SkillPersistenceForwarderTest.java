package com.chatcui.gateway.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.observability.FailureClass;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SkillPersistenceForwarderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void failureClassContractUsesCanonicalTaxonomy() {
        assertEquals(
                java.util.List.of("auth", "bridge", "persistence", "sendback", "unknown"),
                Arrays.stream(FailureClass.values()).map(FailureClass::value).toList());
    }

    @Test
    void forwardsPayloadWithSnakeCaseIdentifiersUnchanged() throws Exception {
        AtomicReference<byte[]> capturedPayload = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(payload -> {
            capturedPayload.set(payload);
            latch.countDown();
        }, (event, error) -> {
        }, Executors.newSingleThreadExecutor());

        SkillTurnForwardEvent event = sampleEvent(3L);
        forwarder.forward(event);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        Map<String, Object> payload = objectMapper.readValue(capturedPayload.get(), new TypeReference<>() {
        });
        assertEquals("session-a", payload.get("session_id"));
        assertEquals("turn-a", payload.get("turn_id"));
        assertEquals(3, payload.get("seq"));
        assertEquals("trace-a", payload.get("trace_id"));
        assertEquals("delta", payload.get("event_type"));
    }

    @Test
    void returnsImmediateAcceptedStateIndependentFromPersistenceOutcome() {
        SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(payload -> {
            Thread.sleep(200);
            throw new IllegalStateException("transport down");
        }, (event, error) -> {
        }, Executors.newSingleThreadExecutor());

        long started = System.nanoTime();
        SkillPersistenceForwarder.ForwardReceipt receipt = forwarder.forward(sampleEvent(4L));
        Duration duration = Duration.ofNanos(System.nanoTime() - started);

        assertTrue(receipt.accepted());
        assertTrue(duration.toMillis() < 100, "forward ack should be independent from persistence result");
    }

    @Test
    void duplicateTupleDoesNotTriggerDuplicateDownstreamAttempts() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(payload -> {
            attempts.incrementAndGet();
            latch.countDown();
        }, (event, error) -> {
        }, Executors.newSingleThreadExecutor());

        SkillTurnForwardEvent event = sampleEvent(10L);
        SkillPersistenceForwarder.ForwardReceipt first = forwarder.forward(event);
        SkillPersistenceForwarder.ForwardReceipt duplicate = forwarder.forward(event);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(first.accepted());
        assertTrue(duplicate.duplicate());
        assertEquals(1, attempts.get());
    }

    private SkillTurnForwardEvent sampleEvent(long seq) {
        return new SkillTurnForwardEvent(
                "tenant-a",
                "client-a",
                "session-a",
                "turn-a",
                seq,
                "trace-a",
                "assistant",
                "delta",
                "payload",
                "skill.turn.delta");
    }
}
