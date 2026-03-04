package com.chatcui.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatcui.gateway.observability.BridgeMetricsRegistry;
import com.chatcui.gateway.observability.FailureClass;
import com.chatcui.gateway.persistence.DeliveryRetryQueue;
import com.chatcui.gateway.persistence.DeliveryStatusReporter;
import com.chatcui.gateway.persistence.SkillPersistenceForwarder;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import com.chatcui.gateway.relay.RelayPublisher;
import com.chatcui.gateway.routing.RouteCasResult;
import com.chatcui.gateway.routing.RouteOwnershipRecord;
import com.chatcui.gateway.routing.RouteOwnershipStore;
import com.chatcui.gateway.runtime.BridgePersistencePublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
            assertEquals(1.0, counterCount(
                    meterRegistry,
                    "chatcui.gateway.route.outcomes",
                    "gateway.route",
                    "route_conflict",
                    FailureClass.BRIDGE,
                    false));
        } finally {
            executor.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void ownerFencedRelaySuccessAndTimeoutOutcomesAreObservable() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BridgeMetricsRegistry metricsRegistry = new BridgeMetricsRegistry(meterRegistry);
        SkillPersistenceForwarder forwarder = new SkillPersistenceForwarder(
                payload -> {
                },
                (event, error) -> {
                },
                Runnable::run);
        DeliveryStatusReporter reporter = new DeliveryStatusReporter();
        RouteOwnershipStore routeStore = fixedRouteStore(new RouteOwnershipRecord(
                "tenant-shared",
                "session-shared-001",
                41L,
                "skill-owner-a",
                "gateway-owner-b",
                "gateway-owner-a",
                Instant.parse("2026-03-04T00:00:00Z")));
        RelayPublisher relaySuccess = envelope -> RelayPublisher.PublishReceipt.accepted(envelope.dedupeKey());
        RelayPublisher relayTimeout = envelope -> {
            throw new IllegalStateException("relay timeout");
        };

        BridgePersistencePublisher fencedPublisher = new BridgePersistencePublisher(
                forwarder,
                reporter,
                metricsRegistry,
                routeStore,
                relaySuccess,
                "gateway-owner-a");
        BridgePersistencePublisher relaySuccessPublisher = new BridgePersistencePublisher(
                forwarder,
                reporter,
                metricsRegistry,
                fixedRouteStore(new RouteOwnershipRecord(
                        "tenant-shared",
                        "session-shared-001",
                        42L,
                        "skill-owner-a",
                        "gateway-owner-b",
                        null,
                        Instant.parse("2026-03-04T00:00:00Z"))),
                relaySuccess,
                "gateway-owner-a");
        BridgePersistencePublisher relayTimeoutPublisher = new BridgePersistencePublisher(
                forwarder,
                reporter,
                metricsRegistry,
                fixedRouteStore(new RouteOwnershipRecord(
                        "tenant-shared",
                        "session-shared-001",
                        43L,
                        "skill-owner-a",
                        "gateway-owner-b",
                        null,
                        Instant.parse("2026-03-04T00:00:00Z"))),
                relayTimeout,
                "gateway-owner-a");

        fencedPublisher.publish("skill.turn.delta", eventWithClient("client-stale", 1L));
        relaySuccessPublisher.publish("skill.turn.delta", eventWithClient("client-stable", 2L));
        try {
            relayTimeoutPublisher.publish("skill.turn.delta", eventWithClient("client-stable", 3L));
        } catch (IllegalStateException ignored) {
            // expected for relay timeout branch
        }

        assertEquals(1.0, counterCount(
                meterRegistry,
                "chatcui.gateway.route.outcomes",
                "gateway.route",
                "owner_fenced",
                FailureClass.BRIDGE,
                false));
        assertEquals(1.0, counterCount(
                meterRegistry,
                "chatcui.gateway.relay.outcomes",
                "gateway.relay",
                "relay_success",
                FailureClass.BRIDGE,
                false));
        assertEquals(1.0, counterCount(
                meterRegistry,
                "chatcui.gateway.relay.outcomes",
                "gateway.relay",
                "relay_timeout",
                FailureClass.BRIDGE,
                true));
        assertEquals(3.0, counterCount(
                meterRegistry,
                "chatcui.gateway.ack.outcomes",
                "gateway.ack",
                "gateway_owner_accepted",
                FailureClass.BRIDGE,
                true));
        assertEquals(2.0, counterCount(
                meterRegistry,
                "chatcui.gateway.ack.outcomes",
                "gateway.ack",
                "client_delivery_timeout",
                FailureClass.BRIDGE,
                true));
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

    private SkillTurnForwardEvent eventWithClient(String clientId, long seq) {
        return new SkillTurnForwardEvent(
                "tenant-shared",
                clientId,
                "session-shared-001",
                "turn-shared-001",
                seq,
                "trace-" + seq,
                "assistant",
                "delta",
                "payload-" + seq,
                "skill.turn.delta");
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

    private RouteOwnershipStore fixedRouteStore(RouteOwnershipRecord record) {
        return new RouteOwnershipStore() {
            @Override
            public Optional<RouteOwnershipRecord> load(String tenantId, String sessionId) {
                return Optional.of(record);
            }

            @Override
            public RouteOwnershipRecord upsert(RouteOwnershipRecord routeRecord, Duration ttl) {
                throw new UnsupportedOperationException("not needed in test");
            }

            @Override
            public RouteCasResult casTransfer(
                    String tenantId,
                    String sessionId,
                    long expectedRouteVersion,
                    String newSkillOwner,
                    String newGatewayOwner,
                    String fencedOwner,
                    Duration ttl) {
                throw new UnsupportedOperationException("not needed in test");
            }
        };
    }
}
