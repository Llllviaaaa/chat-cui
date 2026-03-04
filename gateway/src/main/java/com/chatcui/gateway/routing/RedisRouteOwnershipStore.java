package com.chatcui.gateway.routing;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class RedisRouteOwnershipStore implements RouteOwnershipStore {
    static final String FIELD_UPDATED_AT_EPOCH_MS = "updated_at_epoch_ms";

    static final String CAS_TRANSFER_LUA = """
            local routeKey = KEYS[1]
            if redis.call('EXISTS', routeKey) == 0 then
              return {'missing'}
            end

            local currentVersionRaw = redis.call('HGET', routeKey, 'route_version')
            if currentVersionRaw == false then
              return {'missing'}
            end

            local currentVersion = tonumber(currentVersionRaw)
            local expectedVersion = tonumber(ARGV[1])
            if currentVersion ~= expectedVersion then
              return {
                'conflict',
                tostring(currentVersion),
                redis.call('HGET', routeKey, 'skill_owner') or '',
                redis.call('HGET', routeKey, 'gateway_owner') or '',
                redis.call('HGET', routeKey, 'fenced_owner') or '',
                redis.call('HGET', routeKey, 'updated_at_epoch_ms') or '0'
              }
            end

            local nextVersion = currentVersion + 1
            redis.call('HSET', routeKey,
              'tenant_id', ARGV[2],
              'session_id', ARGV[3],
              'route_version', tostring(nextVersion),
              'skill_owner', ARGV[4],
              'gateway_owner', ARGV[5],
              'fenced_owner', ARGV[6],
              'updated_at_epoch_ms', ARGV[7]
            )
            redis.call('EXPIRE', routeKey, tonumber(ARGV[8]))

            return {
              'applied',
              tostring(nextVersion),
              ARGV[4],
              ARGV[5],
              ARGV[6],
              ARGV[7]
            }
            """;

    private final RedisExecutor redis;
    private final Clock clock;

    public RedisRouteOwnershipStore(StatefulRedisConnection<String, String> connection) {
        this(connection, Clock.systemUTC());
    }

    public RedisRouteOwnershipStore(StatefulRedisConnection<String, String> connection, Clock clock) {
        this(new LettuceRedisExecutor(connection), clock);
    }

    RedisRouteOwnershipStore(RedisExecutor redis, Clock clock) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Optional<RouteOwnershipRecord> load(String tenantId, String sessionId) {
        String key = RouteKeyFactory.routeKey(tenantId, sessionId);
        Map<String, String> row = redis.hgetall(key);
        if (row == null || row.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toRecord(row, normalized(tenantId, RouteOwnershipRecord.FIELD_TENANT_ID), normalized(sessionId, RouteOwnershipRecord.FIELD_SESSION_ID)));
    }

    @Override
    public RouteOwnershipRecord upsert(RouteOwnershipRecord record, Duration ttl) {
        Objects.requireNonNull(record, "record must not be null");
        long ttlSeconds = requireTtl(ttl);
        String key = RouteKeyFactory.routeKey(record.tenantId(), record.sessionId());
        Map<String, String> row = toRedisRow(record);
        redis.hset(key, row);
        redis.expire(key, ttlSeconds);
        return record;
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
        String normalizedTenant = normalized(tenantId, RouteOwnershipRecord.FIELD_TENANT_ID);
        String normalizedSession = normalized(sessionId, RouteOwnershipRecord.FIELD_SESSION_ID);
        String normalizedSkillOwner = normalized(newSkillOwner, RouteOwnershipRecord.FIELD_SKILL_OWNER);
        String normalizedGatewayOwner = normalized(newGatewayOwner, RouteOwnershipRecord.FIELD_GATEWAY_OWNER);
        String normalizedFencedOwner = optional(fencedOwner);
        long ttlSeconds = requireTtl(ttl);
        long nowEpochMs = Instant.now(clock).toEpochMilli();

        String key = RouteKeyFactory.routeKey(normalizedTenant, normalizedSession);
        List<Object> scriptResult = redis.eval(
                CAS_TRANSFER_LUA,
                key,
                Long.toString(expectedRouteVersion),
                normalizedTenant,
                normalizedSession,
                normalizedSkillOwner,
                normalizedGatewayOwner,
                valueForRedis(normalizedFencedOwner),
                Long.toString(nowEpochMs),
                Long.toString(ttlSeconds));

        if (scriptResult == null || scriptResult.isEmpty()) {
            throw new IllegalStateException("CAS transfer script returned empty result");
        }

        String outcome = asString(scriptResult, 0);
        if ("missing".equals(outcome)) {
            return RouteCasResult.missing(expectedRouteVersion);
        }
        if ("applied".equals(outcome)) {
            long routeVersion = parseLong(asString(scriptResult, 1), RouteOwnershipRecord.FIELD_ROUTE_VERSION);
            RouteOwnershipRecord updated = new RouteOwnershipRecord(
                    normalizedTenant,
                    normalizedSession,
                    routeVersion,
                    asString(scriptResult, 2),
                    asString(scriptResult, 3),
                    optional(asString(scriptResult, 4)),
                    Instant.ofEpochMilli(parseLong(asString(scriptResult, 5), FIELD_UPDATED_AT_EPOCH_MS)));
            return RouteCasResult.applied(updated);
        }
        if ("conflict".equals(outcome)) {
            long currentVersion = parseLong(asString(scriptResult, 1), RouteOwnershipRecord.FIELD_ROUTE_VERSION);
            RouteOwnershipRecord current = new RouteOwnershipRecord(
                    normalizedTenant,
                    normalizedSession,
                    currentVersion,
                    asString(scriptResult, 2),
                    asString(scriptResult, 3),
                    optional(asString(scriptResult, 4)),
                    Instant.ofEpochMilli(parseLong(asString(scriptResult, 5), FIELD_UPDATED_AT_EPOCH_MS)));
            return RouteCasResult.versionConflict(expectedRouteVersion, current);
        }

        throw new IllegalStateException("Unexpected CAS transfer outcome: " + outcome);
    }

    private RouteOwnershipRecord toRecord(Map<String, String> row, String tenantId, String sessionId) {
        return new RouteOwnershipRecord(
                tenantId,
                sessionId,
                parseLong(row.get(RouteOwnershipRecord.FIELD_ROUTE_VERSION), RouteOwnershipRecord.FIELD_ROUTE_VERSION),
                row.get(RouteOwnershipRecord.FIELD_SKILL_OWNER),
                row.get(RouteOwnershipRecord.FIELD_GATEWAY_OWNER),
                optional(row.get(RouteOwnershipRecord.FIELD_FENCED_OWNER)),
                Instant.ofEpochMilli(parseLong(row.get(FIELD_UPDATED_AT_EPOCH_MS), FIELD_UPDATED_AT_EPOCH_MS)));
    }

    private Map<String, String> toRedisRow(RouteOwnershipRecord record) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put(RouteOwnershipRecord.FIELD_TENANT_ID, record.tenantId());
        row.put(RouteOwnershipRecord.FIELD_SESSION_ID, record.sessionId());
        row.put(RouteOwnershipRecord.FIELD_ROUTE_VERSION, Long.toString(record.routeVersion()));
        row.put(RouteOwnershipRecord.FIELD_SKILL_OWNER, record.skillOwner());
        row.put(RouteOwnershipRecord.FIELD_GATEWAY_OWNER, record.gatewayOwner());
        row.put(RouteOwnershipRecord.FIELD_FENCED_OWNER, valueForRedis(record.fencedOwner()));
        row.put(FIELD_UPDATED_AT_EPOCH_MS, Long.toString(record.updatedAt().toEpochMilli()));
        return row;
    }

    private static long requireTtl(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        long seconds = ttl.getSeconds();
        if (seconds <= 0) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return seconds;
    }

    private static String normalized(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String optional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String valueForRedis(String value) {
        return value == null ? "" : value;
    }

    private static String asString(List<Object> values, int index) {
        if (index >= values.size()) {
            return "";
        }
        Object value = values.get(index);
        return value == null ? "" : String.valueOf(value);
    }

    private static long parseLong(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be present");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be numeric", ex);
        }
    }

    interface RedisExecutor {
        Map<String, String> hgetall(String key);

        void hset(String key, Map<String, String> fields);

        void expire(String key, long ttlSeconds);

        List<Object> eval(String script, String key, String... args);
    }

    static final class LettuceRedisExecutor implements RedisExecutor {
        private final RedisCommands<String, String> commands;

        LettuceRedisExecutor(StatefulRedisConnection<String, String> connection) {
            this.commands = Objects.requireNonNull(connection, "connection must not be null").sync();
        }

        @Override
        public Map<String, String> hgetall(String key) {
            return commands.hgetall(key);
        }

        @Override
        public void hset(String key, Map<String, String> fields) {
            commands.hset(key, fields);
        }

        @Override
        public void expire(String key, long ttlSeconds) {
            commands.expire(key, ttlSeconds);
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<Object> eval(String script, String key, String... args) {
            List<Object> result = (List<Object>) commands.eval(
                    script,
                    ScriptOutputType.MULTI,
                    new String[] {key},
                    args);
            return result == null ? List.of() : result;
        }
    }
}
