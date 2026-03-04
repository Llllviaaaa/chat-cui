package com.chatcui.skill.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class SkillMetricsRecorder {
    private static final String SEND_BACK_OUTCOME_METRIC = "chatcui.skill.sendback.outcomes";
    private static final String SEND_BACK_DURATION_METRIC = "chatcui.skill.sendback.duration";
    private static final String RELAY_OUTCOME_METRIC = "chatcui.skill.relay.outcomes";
    private static final String TAG_COMPONENT = "component";
    private static final String TAG_FAILURE_CLASS = "failure_class";
    private static final String TAG_OUTCOME = "outcome";
    private static final String TAG_RETRYABLE = "retryable";
    private static final String COMPONENT_SEND_BACK = "skill-service.sendback";
    private static final String COMPONENT_RELAY = "skill-service.relay";

    private final MeterRegistry meterRegistry;

    public SkillMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry is required");
    }

    private SkillMetricsRecorder() {
        this.meterRegistry = null;
    }

    public static SkillMetricsRecorder noop() {
        return new SkillMetricsRecorder();
    }

    public void recordSendbackOutcome(String outcome, FailureClass failureClass, boolean retryable, long durationNanos) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                        SEND_BACK_OUTCOME_METRIC,
                        TAG_COMPONENT,
                        COMPONENT_SEND_BACK,
                        TAG_OUTCOME,
                        normalize(outcome, "unknown"),
                        TAG_FAILURE_CLASS,
                        normalizeFailureClass(failureClass),
                        TAG_RETRYABLE,
                        Boolean.toString(retryable))
                .increment();
        if (durationNanos < 0) {
            return;
        }
        Timer.builder(SEND_BACK_DURATION_METRIC)
                .tag(TAG_COMPONENT, COMPONENT_SEND_BACK)
                .tag(TAG_OUTCOME, normalize(outcome, "unknown"))
                .tag(TAG_FAILURE_CLASS, normalizeFailureClass(failureClass))
                .tag(TAG_RETRYABLE, Boolean.toString(retryable))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordRelayOutcome(String outcome, FailureClass failureClass, boolean retryable) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                        RELAY_OUTCOME_METRIC,
                        TAG_COMPONENT,
                        COMPONENT_RELAY,
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
