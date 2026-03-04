package com.chatcui.gateway.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class BridgeMetricsRegistry {
    private static final String RECONNECT_OUTCOME_METRIC = "chatcui.gateway.bridge.reconnect.outcomes";
    private static final String RESUME_OUTCOME_METRIC = "chatcui.gateway.bridge.resume.outcomes";
    private static final String PERSISTENCE_OUTCOME_METRIC = "chatcui.gateway.persistence.retry.outcomes";
    private static final String PERSISTENCE_DURATION_METRIC = "chatcui.gateway.persistence.retry.duration";

    private static final String TAG_COMPONENT = "component";
    private static final String TAG_FAILURE_CLASS = "failure_class";
    private static final String TAG_OUTCOME = "outcome";
    private static final String TAG_RETRYABLE = "retryable";

    private final MeterRegistry meterRegistry;

    public BridgeMetricsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry is required");
    }

    private BridgeMetricsRegistry() {
        this.meterRegistry = null;
    }

    public static BridgeMetricsRegistry noop() {
        return new BridgeMetricsRegistry();
    }

    public void recordReconnectOutcome(String outcome, FailureClass failureClass, boolean retryable) {
        increment(RECONNECT_OUTCOME_METRIC, "gateway.bridge.reconnect", outcome, failureClass, retryable);
    }

    public void recordResumeOutcome(String outcome, FailureClass failureClass, boolean retryable) {
        increment(RESUME_OUTCOME_METRIC, "gateway.bridge.resume", outcome, failureClass, retryable);
    }

    public void recordPersistenceOutcome(String outcome, FailureClass failureClass, boolean retryable) {
        increment(PERSISTENCE_OUTCOME_METRIC, "gateway.persistence.retry", outcome, failureClass, retryable);
    }

    public void recordPersistenceDuration(String outcome, FailureClass failureClass, boolean retryable, long durationNanos) {
        if (meterRegistry == null || durationNanos < 0) {
            return;
        }
        Timer.builder(PERSISTENCE_DURATION_METRIC)
                .tag(TAG_COMPONENT, "gateway.persistence.retry")
                .tag(TAG_OUTCOME, normalize(outcome, "unknown"))
                .tag(TAG_FAILURE_CLASS, normalizeFailureClass(failureClass))
                .tag(TAG_RETRYABLE, Boolean.toString(retryable))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private void increment(String metricName, String component, String outcome, FailureClass failureClass, boolean retryable) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                        metricName,
                        TAG_COMPONENT,
                        component,
                        TAG_OUTCOME,
                        normalize(outcome, "unknown"),
                        TAG_FAILURE_CLASS,
                        normalizeFailureClass(failureClass),
                        TAG_RETRYABLE,
                        Boolean.toString(retryable))
                .increment();
    }

    private String normalizeFailureClass(FailureClass failureClass) {
        return failureClass == null ? FailureClass.UNKNOWN.value() : failureClass.value();
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
