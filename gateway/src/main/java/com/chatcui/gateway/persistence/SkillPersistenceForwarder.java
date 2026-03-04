package com.chatcui.gateway.persistence;

import com.chatcui.gateway.persistence.model.SkillTurnForwardEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class SkillPersistenceForwarder {
    private final IngestClient ingestClient;
    private final RetryQueue retryQueue;
    private final DeliveryStatusSink deliveryStatusSink;
    private final Executor executor;
    private final ObjectMapper objectMapper;
    private final Set<String> acceptedTuples = ConcurrentHashMap.newKeySet();

    public SkillPersistenceForwarder(IngestClient ingestClient, RetryQueue retryQueue, Executor executor) {
        this(ingestClient, retryQueue, DeliveryStatusSink.noop(), executor, new ObjectMapper());
    }

    SkillPersistenceForwarder(
            IngestClient ingestClient,
            RetryQueue retryQueue,
            DeliveryStatusSink deliveryStatusSink,
            Executor executor,
            ObjectMapper objectMapper) {
        this.ingestClient = ingestClient;
        this.retryQueue = retryQueue;
        this.deliveryStatusSink = deliveryStatusSink;
        this.executor = executor;
        this.objectMapper = objectMapper;
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
            retryQueue.enqueue(event, error);
        }
    }

    private String tupleKey(SkillTurnForwardEvent event) {
        return event.sessionId() + "|" + event.turnId() + "|" + event.seq();
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
}
