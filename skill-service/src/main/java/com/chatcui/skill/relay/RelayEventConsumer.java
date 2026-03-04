package com.chatcui.skill.relay;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RelayEventConsumer {
    private final String consumerGroup;
    private final String localSkillOwner;
    private final RelayDispatchService relayDispatchService;
    private final TupleDedupeStore tupleDedupeStore;
    private final AckClient ackClient;

    public RelayEventConsumer(
            String consumerGroup,
            String localSkillOwner,
            RelayDispatchService relayDispatchService,
            TupleDedupeStore tupleDedupeStore,
            AckClient ackClient) {
        this.consumerGroup = requireValue(consumerGroup, "consumer_group");
        this.localSkillOwner = requireValue(localSkillOwner, "local_skill_owner");
        this.relayDispatchService = Objects.requireNonNull(relayDispatchService, "relayDispatchService must not be null");
        this.tupleDedupeStore = Objects.requireNonNull(tupleDedupeStore, "tupleDedupeStore must not be null");
        this.ackClient = Objects.requireNonNull(ackClient, "ackClient must not be null");
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
            return new ConsumeOutcome(ConsumeStatus.DUPLICATE_DROPPED, true, dedupeTuple);
        }

        RelayDispatchService.DispatchOutcome dispatchOutcome =
                relayDispatchService.dispatch(record.event(), localSkillOwner);
        if (dispatchOutcome.status() == RelayDispatchService.DispatchStatus.PENDING_RETRY) {
            return new ConsumeOutcome(ConsumeStatus.PENDING_RETRY, false, dedupeTuple);
        }

        ack(record);
        if (dispatchOutcome.status() == RelayDispatchService.DispatchStatus.SKIPPED_NOT_OWNER) {
            return new ConsumeOutcome(ConsumeStatus.SKIPPED_NOT_OWNER, true, dedupeTuple);
        }
        return new ConsumeOutcome(ConsumeStatus.DISPATCHED, true, dedupeTuple);
    }

    private void ack(StreamRecord record) {
        ackClient.ack(record.streamKey(), consumerGroup, record.messageId());
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
        PENDING_RETRY
    }

    public record StreamRecord(String streamKey, String messageId, RelayDispatchService.RelayEvent event) {
        public StreamRecord {
            streamKey = requireValue(streamKey, "stream_key");
            messageId = requireValue(messageId, "message_id");
            event = Objects.requireNonNull(event, "event must not be null");
        }
    }

    public record ConsumeOutcome(ConsumeStatus status, boolean acked, String dedupeKey) {
        public ConsumeOutcome {
            status = Objects.requireNonNull(status, "status must not be null");
            dedupeKey = requireValue(dedupeKey, "dedupe_key");
        }
    }

    @FunctionalInterface
    public interface TupleDedupeStore {
        boolean markIfAbsent(String dedupeTuple);
    }

    @FunctionalInterface
    public interface AckClient {
        void ack(String streamKey, String groupName, String messageId);
    }

    public static final class InMemoryTupleDedupeStore implements TupleDedupeStore {
        private final Set<String> tuples = ConcurrentHashMap.newKeySet();

        @Override
        public boolean markIfAbsent(String dedupeTuple) {
            return tuples.add(requireValue(dedupeTuple, "dedupe_tuple"));
        }
    }
}
