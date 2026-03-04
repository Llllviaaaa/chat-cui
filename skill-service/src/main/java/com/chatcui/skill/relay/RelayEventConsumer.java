package com.chatcui.skill.relay;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RelayEventConsumer {
    private static final Duration DEFAULT_UNKNOWN_OWNER_REPLAY_WINDOW = Duration.ofMinutes(15);

    private final String consumerGroup;
    private final String localSkillOwner;
    private final RelayDispatchService relayDispatchService;
    private final TupleDedupeStore tupleDedupeStore;
    private final AckClient ackClient;
    private final UnknownOwnerRecoveryTracker unknownOwnerRecoveryTracker;
    private final Clock clock;

    public RelayEventConsumer(
            String consumerGroup,
            String localSkillOwner,
            RelayDispatchService relayDispatchService,
            TupleDedupeStore tupleDedupeStore,
            AckClient ackClient) {
        this(
                consumerGroup,
                localSkillOwner,
                relayDispatchService,
                tupleDedupeStore,
                ackClient,
                new InMemoryUnknownOwnerRecoveryTracker(DEFAULT_UNKNOWN_OWNER_REPLAY_WINDOW),
                Clock.systemUTC());
    }

    RelayEventConsumer(
            String consumerGroup,
            String localSkillOwner,
            RelayDispatchService relayDispatchService,
            TupleDedupeStore tupleDedupeStore,
            AckClient ackClient,
            UnknownOwnerRecoveryTracker unknownOwnerRecoveryTracker,
            Clock clock) {
        this.consumerGroup = requireValue(consumerGroup, "consumer_group");
        this.localSkillOwner = requireValue(localSkillOwner, "local_skill_owner");
        this.relayDispatchService = Objects.requireNonNull(relayDispatchService, "relayDispatchService must not be null");
        this.tupleDedupeStore = Objects.requireNonNull(tupleDedupeStore, "tupleDedupeStore must not be null");
        this.ackClient = Objects.requireNonNull(ackClient, "ackClient must not be null");
        this.unknownOwnerRecoveryTracker =
                Objects.requireNonNull(unknownOwnerRecoveryTracker, "unknownOwnerRecoveryTracker must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ConsumeOutcome consume(StreamRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        String dedupeTuple = RelayDispatchService.RelayEvent.dedupeTuple(
                record.event().sessionId(),
                record.event().turnId(),
                record.event().seq(),
                record.event().topic());
        if (!tupleDedupeStore.markIfAbsent(dedupeTuple)) {
            ack(record);
            return new ConsumeOutcome(ConsumeStatus.DUPLICATE_DROPPED, true, dedupeTuple, null, null);
        }

        RelayDispatchService.DispatchOutcome dispatchOutcome =
                relayDispatchService.dispatch(record.event(), localSkillOwner);
        if (dispatchOutcome.status() == RelayDispatchService.DispatchStatus.PENDING_RETRY) {
            return pendingRetryOutcome(record, dedupeTuple, dispatchOutcome);
        }

        ack(record);
        unknownOwnerRecoveryTracker.clear(dedupeTuple);
        if (dispatchOutcome.status() == RelayDispatchService.DispatchStatus.SKIPPED_NOT_OWNER) {
            return new ConsumeOutcome(ConsumeStatus.SKIPPED_NOT_OWNER, true, dedupeTuple, null, null);
        }
        return new ConsumeOutcome(ConsumeStatus.DISPATCHED, true, dedupeTuple, null, null);
    }

    private void ack(StreamRecord record) {
        ackClient.ack(record.streamKey(), consumerGroup, record.messageId());
    }

    private ConsumeOutcome pendingRetryOutcome(
            StreamRecord record,
            String dedupeTuple,
            RelayDispatchService.DispatchOutcome dispatchOutcome) {
        tupleDedupeStore.release(dedupeTuple);
        if ("route_missing".equals(dispatchOutcome.reason())) {
            RecoveryDecision recoveryDecision =
                    unknownOwnerRecoveryTracker.register(dedupeTuple, Instant.now(clock));
            if (recoveryDecision == RecoveryDecision.REPLAY_WINDOW_EXPIRED) {
                ack(record);
                unknownOwnerRecoveryTracker.clear(dedupeTuple);
                return new ConsumeOutcome(
                        ConsumeStatus.REPLAY_WINDOW_EXPIRED,
                        true,
                        dedupeTuple,
                        "ROUTE_REPLAY_WINDOW_EXPIRED",
                        "restart_session");
            }
        }
        return new ConsumeOutcome(ConsumeStatus.PENDING_RETRY, false, dedupeTuple, null, null);
    }

    private static String requireValue(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    public enum ConsumeStatus {
        DISPATCHED,
        SKIPPED_NOT_OWNER,
        DUPLICATE_DROPPED,
        PENDING_RETRY,
        REPLAY_WINDOW_EXPIRED
    }

    public record StreamRecord(String streamKey, String messageId, RelayDispatchService.RelayEvent event) {
        public StreamRecord {
            streamKey = requireValue(streamKey, "stream_key");
            messageId = requireValue(messageId, "message_id");
            event = Objects.requireNonNull(event, "event must not be null");
        }
    }

    public record ConsumeOutcome(
            ConsumeStatus status,
            boolean acked,
            String dedupeKey,
            String errorCode,
            String nextAction) {
        public ConsumeOutcome {
            status = Objects.requireNonNull(status, "status must not be null");
            dedupeKey = requireValue(dedupeKey, "dedupe_key");
            errorCode = normalizeOptional(errorCode);
            nextAction = normalizeOptional(nextAction);
        }
    }

    @FunctionalInterface
    public interface TupleDedupeStore {
        boolean markIfAbsent(String dedupeTuple);

        default void release(String dedupeTuple) {
        }
    }

    @FunctionalInterface
    public interface AckClient {
        void ack(String streamKey, String groupName, String messageId);
    }

    @FunctionalInterface
    public interface UnknownOwnerRecoveryTracker {
        RecoveryDecision register(String dedupeTuple, Instant now);

        default void clear(String dedupeTuple) {
        }
    }

    public enum RecoveryDecision {
        IN_WINDOW,
        REPLAY_WINDOW_EXPIRED
    }

    public static final class InMemoryTupleDedupeStore implements TupleDedupeStore {
        private final Set<String> tuples = ConcurrentHashMap.newKeySet();

        @Override
        public boolean markIfAbsent(String dedupeTuple) {
            return tuples.add(requireValue(dedupeTuple, "dedupe_tuple"));
        }

        @Override
        public void release(String dedupeTuple) {
            tuples.remove(requireValue(dedupeTuple, "dedupe_tuple"));
        }
    }

    public static final class InMemoryUnknownOwnerRecoveryTracker implements UnknownOwnerRecoveryTracker {
        private final Duration replayWindow;
        private final Map<String, Instant> firstSeenByTuple = new ConcurrentHashMap<>();

        public InMemoryUnknownOwnerRecoveryTracker(Duration replayWindow) {
            this.replayWindow = Objects.requireNonNull(replayWindow, "replayWindow must not be null");
            if (replayWindow.isZero() || replayWindow.isNegative()) {
                throw new IllegalArgumentException("replayWindow must be positive");
            }
        }

        @Override
        public RecoveryDecision register(String dedupeTuple, Instant now) {
            String normalizedTuple = requireValue(dedupeTuple, "dedupe_tuple");
            Instant observedAt = Objects.requireNonNull(now, "now must not be null");
            Instant firstSeen = firstSeenByTuple.computeIfAbsent(normalizedTuple, ignored -> observedAt);
            if (!observedAt.isBefore(firstSeen.plus(replayWindow))) {
                return RecoveryDecision.REPLAY_WINDOW_EXPIRED;
            }
            return RecoveryDecision.IN_WINDOW;
        }

        @Override
        public void clear(String dedupeTuple) {
            firstSeenByTuple.remove(requireValue(dedupeTuple, "dedupe_tuple"));
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
