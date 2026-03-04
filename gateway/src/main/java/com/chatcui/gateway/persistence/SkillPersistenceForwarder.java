package com.chatcui.gateway.persistence;

import com.chatcui.gateway.observability.FailureClass;
import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class SkillPersistenceForwarder {
    private static final System.Logger LOGGER = System.getLogger(SkillPersistenceForwarder.class.getName());
    private final IngestClient ingestClient;
    private final RetryQueue retryQueue;
    private final DeliveryStatusSink deliveryStatusSink;
    private final FailureLogSink failureLogSink;
    private final Executor executor;
    private final ObjectMapper objectMapper;
    private final Set<String> acceptedTuples = ConcurrentHashMap.newKeySet();

    public SkillPersistenceForwarder(IngestClient ingestClient, RetryQueue retryQueue, Executor executor) {
        this(ingestClient, retryQueue, DeliveryStatusSink.noop(), executor, new ObjectMapper(), null);
    }

    SkillPersistenceForwarder(
            IngestClient ingestClient,
            RetryQueue retryQueue,
            DeliveryStatusSink deliveryStatusSink,
            Executor executor,
            ObjectMapper objectMapper) {
        this(ingestClient, retryQueue, deliveryStatusSink, executor, objectMapper, null);
    }

    SkillPersistenceForwarder(
            IngestClient ingestClient,
            RetryQueue retryQueue,
            DeliveryStatusSink deliveryStatusSink,
            Executor executor,
            ObjectMapper objectMapper,
            FailureLogSink failureLogSink) {
        this.ingestClient = ingestClient;
        this.retryQueue = retryQueue;
        this.deliveryStatusSink = deliveryStatusSink;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.failureLogSink = failureLogSink == null ? this::logStructuredFailure : failureLogSink;
    }

    public ForwardReceipt forward(SkillTurnForwardEvent event) {
        String tupleKey = tupleKey(event);
        if (!acceptedTuples.add(tupleKey)) {
            return new ForwardReceipt(true, true, "accepted");
        }
        deliveryStatusSink.pending(event);
        CompletableFuture.runAsync(() -> deliver(event), executor);
        return new ForwardReceipt(true, false, "accepted");
    }

    private void deliver(SkillTurnForwardEvent event) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            ingestClient.send(payload);
            deliveryStatusSink.saved(event);
        } catch (Exception error) {
            deliveryStatusSink.failed(event, error);
            failureLogSink.record(toFailureEnvelope(event));
            retryQueue.enqueue(event, error);
        }
    }

    private void logStructuredFailure(Map<String, Object> envelope) {
        try {
            LOGGER.log(System.Logger.Level.ERROR, objectMapper.writeValueAsString(envelope));
        } catch (Exception serializationError) {
            LOGGER.log(System.Logger.Level.ERROR, envelope.toString());
        }
    }

    private Map<String, Object> toFailureEnvelope(SkillTurnForwardEvent event) {
        return Map.ofEntries(
                Map.entry("tenant_id", stringOrUnknown(event.tenantId(), "tenant-unknown")),
                Map.entry("client_id", stringOrUnknown(event.clientId(), "client-unknown")),
                Map.entry("session_id", stringOrUnknown(event.sessionId(), "session-unknown")),
                Map.entry("turn_id", stringOrUnknown(event.turnId(), "turn-unknown")),
                Map.entry("seq", event.seq()),
                Map.entry("trace_id", stringOrUnknown(event.traceId(), "trace-unknown")),
                Map.entry("error_code", "PERSISTENCE_FORWARD_FAILED"),
                Map.entry("component", "gateway.persistence.forwarder"),
                Map.entry("status", "failed"),
                Map.entry("failure_class", FailureClass.PERSISTENCE.value()),
                Map.entry("retryable", FailureClass.PERSISTENCE.retryableDefault()));
    }

    private String stringOrUnknown(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String tupleKey(SkillTurnForwardEvent event) {
        return event.sessionId() + "|" + event.turnId() + "|" + event.seq() + "|" + event.topic();
    }

    public record ForwardReceipt(boolean accepted, boolean duplicate, String receiveState) {
    }

    @FunctionalInterface
    public interface IngestClient {
        void send(byte[] payload) throws Exception;
    }

    @FunctionalInterface
    public interface RetryQueue {
        void enqueue(SkillTurnForwardEvent event, Exception error);
    }

    public interface DeliveryStatusSink {
        static DeliveryStatusSink noop() {
            return new DeliveryStatusSink() {
            };
        }

        default void pending(SkillTurnForwardEvent event) {
        }

        default void saved(SkillTurnForwardEvent event) {
        }

        default void failed(SkillTurnForwardEvent event, Exception error) {
        }
    }

    @FunctionalInterface
    public interface FailureLogSink {
        void record(Map<String, Object> envelope);
    }
}
