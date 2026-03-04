package com.chatcui.gateway.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class RedisRelayPublisher implements RelayPublisher {
    static final String STREAM_PREFIX = "chatcui:relay:first-hop:";
    static final String DEDUPE_PREFIX = "chatcui:relay:dedupe:";
    private static final String DEDUPE_SEPARATOR = "||";

    private final RedisExecutor redis;
    private final ObjectMapper objectMapper;
    private final Duration dedupeTtl;

    public RedisRelayPublisher(StatefulRedisConnection<String, String> connection) {
        this(new LettuceRedisExecutor(connection), new ObjectMapper(), Duration.ofMinutes(15));
    }

    public RedisRelayPublisher(StatefulRedisConnection<String, String> connection, Duration dedupeTtl) {
        this(new LettuceRedisExecutor(connection), new ObjectMapper(), dedupeTtl);
    }

    RedisRelayPublisher(RedisExecutor redis, ObjectMapper objectMapper, Duration dedupeTtl) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.dedupeTtl = Objects.requireNonNull(dedupeTtl, "dedupeTtl must not be null");
        if (dedupeTtl.isNegative() || dedupeTtl.isZero()) {
            throw new IllegalArgumentException("dedupeTtl must be positive");
        }
    }

    @Override
    public PublishReceipt publishFirstHop(RelayEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        String dedupeRedisKey = dedupeRedisKey(envelope.partitionKey(), envelope.dedupeKey());
        boolean accepted = redis.setNxWithTtl(
                dedupeRedisKey,
                envelope.traceId(),
                dedupeTtl.getSeconds());
        if (!accepted) {
            return PublishReceipt.duplicate(envelope.dedupeKey());
        }
        String streamKey = streamKey(envelope.tenantId(), envelope.sessionId());
        String payload = serializeEnvelope(envelope);
        String messageId = redis.xadd(streamKey, toFields(envelope, payload));
        return PublishReceipt.accepted(envelope.dedupeKey(), messageId);
    }

    private String serializeEnvelope(RelayEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize relay envelope", ex);
        }
    }

    private Map<String, String> toFields(RelayEnvelope envelope, String payload) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("tenant_id", envelope.tenantId());
        fields.put("client_id", envelope.clientId());
        fields.put("session_id", envelope.sessionId());
        fields.put("turn_id", envelope.turnId());
        fields.put("seq", Long.toString(envelope.seq()));
        fields.put("topic", envelope.topic());
        fields.put("trace_id", envelope.traceId());
        fields.put("route_version", Long.toString(envelope.routeVersion()));
        fields.put("source_gateway_owner", envelope.sourceGatewayOwner());
        fields.put("target_skill_owner", envelope.targetSkillOwner());
        fields.put("target_gateway_owner", envelope.targetGatewayOwner());
        fields.put("hop", envelope.hop());
        fields.put("partition_key", envelope.partitionKey());
        fields.put("dedupe_key", envelope.dedupeKey());
        fields.put("actor", envelope.actor());
        fields.put("event_type", envelope.eventType());
        fields.put("payload", envelope.payload());
        fields.put("reason_code", envelope.reasonCode() == null ? "" : envelope.reasonCode());
        fields.put("next_action", envelope.nextAction() == null ? "" : envelope.nextAction());
        fields.put("envelope_json", payload);
        return fields;
    }

    static String streamKey(String tenantId, String sessionId) {
        String partition = RelayEnvelope.partitionKey(tenantId, sessionId);
        return STREAM_PREFIX + "{" + partition + "}";
    }

    static String dedupeRedisKey(String partitionKey, String dedupeTuple) {
        return DEDUPE_PREFIX + "{" + partitionKey + "}" + DEDUPE_SEPARATOR + dedupeTuple;
    }

    interface RedisExecutor {
        boolean setNxWithTtl(String key, String value, long ttlSeconds);

        String xadd(String streamKey, Map<String, String> fields);
    }

    static final class LettuceRedisExecutor implements RedisExecutor {
        private final RedisCommands<String, String> commands;

        LettuceRedisExecutor(StatefulRedisConnection<String, String> connection) {
            this.commands = Objects.requireNonNull(connection, "connection must not be null").sync();
        }

        @Override
        public boolean setNxWithTtl(String key, String value, long ttlSeconds) {
            String result = commands.set(key, value, SetArgs.Builder.nx().ex(ttlSeconds));
            return "OK".equalsIgnoreCase(result);
        }

        @Override
        public String xadd(String streamKey, Map<String, String> fields) {
            return commands.xadd(streamKey, fields);
        }
    }
}
