# Phase 08: ai-gateway-skill-service-opencode - Research

**Researched:** 2026-03-04  
**Domain:** Multi-instance routing, owner fencing, cross-instance relay, and deterministic delivery semantics for OpenCode message paths  
**Confidence:** HIGH (repo-local constraints and baseline), MEDIUM-HIGH (Redis/event-bus implementation details)

<user_constraints>
## User Constraints (from 08-CONTEXT.md)

### Locked Decisions

### 路由归属与跨实例主链路
- 路由键模型沿用并锁定为 `tenant_id + session_id`。
- 会话 owner 允许迁移，但必须 Fence 旧 owner（单会话同一时刻仅一个有效 owner）。
- 会话路由真相源采用 Redis 路由表。
- 路由目标记录粒度为完整链路目标（`skill-service owner + gateway owner`）。
- 当 OpenCode 消息进入非目标网关实例时，跨实例转发主通道采用事件总线。
- 跨实例首跳优先投递到 `skill-service owner`，再由其按绑定关系下发到目标 `gateway/client`。
- 事件总线分区键采用 `tenant_id + session_id`，保证单会话顺序。
- 跨 hop 去重键统一采用 `session_id + turn_id + seq + topic`。
- 对于未知 owner（TTL 过期/实例瞬断），默认策略为短暂排队并重查路由，不立即失败。

### 迁移切换与 Fence 规则
- owner 迁移触发机制为“显式切换优先 + 超时兜底”。
- Fence 生效时刻为“路由版本 CAS 成功后立即生效”。
- 旧 owner 在 Fence 后收到消息时，必须拒绝并返回 `OWNER_FENCED`，由上游按新路由重投。
- 迁移期间在途消息采用“冻结旧 owner + 基于 resume anchor 在新 owner 重放续传”。
- 继续沿用 Phase 6 的 resume anchor 语义：`session_id + turn_id + seq`。

### 投递确认与失败语义
- 精准投递成功采用两阶段确认语义：
  1) owner gateway 已接收；
  2) 已下发客户端（带超时状态）。
- 跨实例到客户端的默认投递保证为“至少一次 + 全链路去重”。
- 补偿超时后，返回确定性错误包，不使用静默失败：
  - `error_code`
  - `next_action`
  - `trace_id`
  - `route_version`

### 离线补偿与回放窗口
- 补偿缓冲介质采用 Redis Stream（消费组方式）。
- 默认回放窗口 TTL 设为 15 分钟。
- 超出回放窗口后进入确定性失败分支，不做无限重试。

### Claude's Discretion
- Redis 路由表与 Stream 的具体 key 命名规范、前缀和版本号编码方式。
- 事件总线主题命名、分区数建议和消费组命名细则。
- `route_version` 字段具体格式（数值版本/时间戳版本）与冲突重试退避参数。
- 两阶段确认中客户端下发超时阈值、重试节奏与告警阈值。

### Deferred Ideas (OUT OF SCOPE)
- 多租户限流策略细粒度治理（如每租户每会话速率控制）可在后续 phase 单独展开。
- 告警阈值和运营看板细化可在后续 observability phase 独立建设。
</user_constraints>

## Summary

Phase 08 should be planned as a distributed consistency and routing-control phase, not as a UI/protocol feature phase. The current codebase already has strong local/session semantics (`ResumeCoordinator`, compensation branches, deterministic `reason_code/next_action`, idempotent persistence, structured metrics), but ownership and routing truth are still process-local and cannot guarantee correctness under multi-instance topology.

Given locked decisions, the safest and lowest-change implementation path is: use Redis as the single routing source of truth (with CAS-based owner migration and fence semantics), use Redis Streams consumer-groups for cross-instance relay and offline compensation queues, and preserve existing tuple-based dedupe + resume-anchor semantics across every hop. This keeps Phase 8 aligned with Phase 6 reliability contracts and avoids introducing a second consistency model.

**Primary recommendation:** plan Wave 0 around contract lock-in for `route_version`, key/topic naming, and fence/error envelopes; then implement a Redis-backed routing coordinator + stream relay pipeline + two-stage delivery ack state machine with strict observability and deterministic terminal outcomes.

## Project Context Checks

- `CLAUDE.md`: not present.
- `.claude/skills`: not present.
- `.agents/skills`: not present.
- `.planning/config.json`: `workflow.nyquist_validation` is not enabled; skip Validation Architecture section.

## Planning-Critical Findings

1. **Current owner/routing state is in-memory and single-process only.**  
   `ResumeCoordinator` stores anchors/owners in local `ConcurrentHashMap`; this cannot enforce single effective owner across multiple gateway instances.

2. **Existing semantics are already close to required Phase 8 behavior.**  
   Continue/drop-duplicate/compensate-gap/terminal-failure flows already exist in gateway runtime + tests. Phase 8 should extend these semantics to distributed routing, not redesign them.

3. **Redis is already a locked decision for route truth and compensation stream.**  
   Planning should avoid adding a different primary consistency store for route ownership unless proven necessary.

4. **Event bus delivery must not use Redis Pub/Sub for critical relay paths.**  
   Redis Pub/Sub is at-most-once and does not provide ack/replay guarantees, which conflicts with locked "at least once + full-link dedupe" requirements.

5. **Redis Streams consumer-group mechanics map directly to phase requirements.**  
   `XREADGROUP`/`XACK`/`XPENDING`/`XAUTOCLAIM` provide pending-entry recovery and retry lanes needed for unknown-owner queuing and instance failover.

6. **Route migration correctness depends on atomic CAS + fence update.**  
   Any non-atomic owner switch risks dual-owner split-brain and ghost writes.

7. **Clustered Redis introduces key-slot constraints that must be designed up front.**  
   Multi-key operations and scripts can fail with `CROSSSLOT` unless hash tags/key design are planned before implementation.

8. **Gateway and skill-service already expose failure-class and low-cardinality metric conventions.**  
   Phase 8 should extend current `failure_class/retryable/component/outcome` taxonomy, not introduce new telemetry dimensions.

## Requirement-ID Gap (Must Be Closed Early)

Roadmap currently lists Phase 8 requirement IDs as `TBD`. Planning quality and downstream verification will be weak without explicit IDs.

Suggested provisional IDs for Wave 0:

| Suggested ID | Intent | Completion Signal |
|---|---|---|
| P08-ROUTE-01 | Redis route-table truth with owner pair (`skill-service owner + gateway owner`) and `route_version` CAS updates | Route read/write APIs + CAS conflict tests + fence timestamp/version evidence |
| P08-FENCE-01 | Fence old owner immediately after CAS success; old owner returns `OWNER_FENCED` deterministically | Conflict tests prove rejection path and deterministic envelope |
| P08-RELAY-01 | Non-target gateway relays via event bus to `skill-service owner` first, then target `gateway/client` | Cross-instance integration test over two gateway instances + one skill-service instance |
| P08-DEDUPE-01 | End-to-end dedupe key `session_id + turn_id + seq + topic` applied across all relay hops | Duplicate replay tests across retries/reconnects |
| P08-ACK-01 | Two-stage delivery acknowledgement (`gateway accepted` then `client delivered/timeout`) with deterministic failure envelope | Ack-state transition tests including timeout branch |
| P08-RECOVERY-01 | Unknown owner queue + route recheck + bounded replay window (15m) + deterministic terminal failure after window | TTL/retry/terminal path tests with fault injection |
| P08-OBS-01 | Route/fence/ack metrics and logs extend existing trace + failure taxonomy | Cross-service metrics/log assertions with stable low-card tags |

## Standard Stack

### Core

| Component | Version | Purpose | Why Standard |
|---|---|---|---|
| Redis (route table + relay/compensation streams) | `>= 6.2` required, `8.6+` optional features | Route truth, CAS/fence support, consumer-group replay, unknown-owner queueing | Locked by context; Streams + consumer groups map naturally to at-least-once delivery and replay controls |
| `io.lettuce:lettuce-core` (gateway) | Pin in phase plan (align with Redis target) | Non-Spring gateway Redis client for keys/scripts/streams | Gateway is currently plain Java module; Lettuce is mature and documented for reconnect semantics |
| `spring-boot-starter-data-redis` (skill-service) | Boot 3.4.6 BOM-managed | Skill-service stream consumers/producers + route lookups | Fits existing Spring Boot stack and reduces custom wiring |
| Existing tuple contract (`tenant_id/client_id/session_id/turn_id/seq/trace_id`) | Existing | Cross-hop identity + dedupe continuity | Already enforced by current runtime/service tests and DTOs |
| Existing failure envelope (`reason_code/next_action`) | Existing | Deterministic error handling | Already adopted in Phase 6 runtime and service behavior |

### Supporting

| Component | Purpose | When to Use |
|---|---|---|
| Lua scripts (`EVAL`) | Atomic route CAS + fence updates | Multi-key compare-and-set route migration and owner transfer |
| Redis Streams consumer groups (`XREADGROUP`, `XACK`, `XPENDING`, `XAUTOCLAIM`) | At-least-once relay with pending-entry takeover | Cross-instance relay and unknown-owner short-queue/replay workers |
| Micrometer existing registries (`gateway`, `skill-service`) | Route/fence/ack metrics with low cardinality tags | Reuse and extend current observability taxonomy |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|---|---|---|
| Redis Streams as main event bus | Kafka (or existing enterprise MQ) | Kafka gives stronger high-throughput partition tooling but adds new infra/ops and broader blast radius in this phase |
| Numeric `route_version` CAS | Timestamp-based version | Numeric monotonic versions are simpler for deterministic conflict handling and retries |
| Dual-stream relay phases | One stream with stage field | One stream is simpler operationally, but dual-stream may isolate ownership boundaries better; choose in Wave 0 and keep consistent |

**Dependency additions (planning baseline):**

```xml
<!-- gateway/pom.xml -->
<dependency>
  <groupId>io.lettuce</groupId>
  <artifactId>lettuce-core</artifactId>
</dependency>

<!-- skill-service/pom.xml -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

## Architecture Patterns

### Recommended Project Structure

```text
gateway/
├─ src/main/java/com/chatcui/gateway/routing/      # Redis route truth, CAS/fence coordinator
├─ src/main/java/com/chatcui/gateway/relay/        # stream producer/consumer, hop dedupe
└─ src/main/java/com/chatcui/gateway/runtime/      # existing resume semantics integration

skill-service/
├─ src/main/java/com/chatcui/skill/relay/          # first-hop owner consume + second-hop dispatch
├─ src/main/java/com/chatcui/skill/routing/        # route lookup helpers (if needed)
└─ src/main/java/com/chatcui/skill/service/        # existing persistence/sendback integration
```

### Pattern 1: Redis Route Truth + CAS Fence

**What:** Keep per-session route ownership in Redis as authoritative state, updated only via CAS-versioned write path.  
**When to use:** Any route assignment, owner migration, or fence activation.

**Recommended route record fields:**

- `tenant_id`
- `session_id`
- `route_version` (monotonic integer)
- `skill_owner_instance_id`
- `gateway_owner_instance_id`
- `fenced_owner_instance_id` (or prior owner metadata)
- `updated_at_epoch_ms`
- `expires_at_epoch_ms` (if TTL-based expiry is required)

**Key rule:** route mutation and fence activation happen in one atomic operation.

### Pattern 2: Two-Hop Relay Topology (Locked)

**What:** Non-target gateway publishes relay event to event bus; `skill-service owner` consumes first; then forwards to target `gateway/client` according to route record.  
**When to use:** OpenCode message lands on gateway instance not owning target client connection.

**Event envelope required fields:**

- Existing base fields: `tenant_id`, `client_id`, `session_id`, `turn_id`, `seq`, `trace_id`, `topic`
- Phase 8 additions: `route_version`, `hop`, `relay_from`, `relay_to`, `deliver_stage`

### Pattern 3: Unknown-Owner Short Queue + Recheck

**What:** If route owner is missing/expired, enqueue to short-lived retry stream and re-resolve route until success or replay window timeout.  
**When to use:** TTL expiry, transient instance disconnect, route cache miss.

**Key rule:** unknown owner is retryable within 15-minute replay window; after window, produce deterministic terminal failure envelope.

### Pattern 4: Two-Stage Delivery ACK State Machine

**What:** Persist and emit two separate confirmations:

1. `gateway_owner_accepted`
2. `client_delivered` or `client_delivery_timeout`

**When to use:** Every cross-instance relay-to-client path.

**Key rule:** caller-visible success requires stage-2 success; otherwise return deterministic failure payload containing `error_code`, `next_action`, `trace_id`, `route_version`.

### Pattern 5: End-to-End Idempotency at Every Hop

**What:** Use locked dedupe tuple key `session_id + turn_id + seq + topic` across gateway->bus->skill-service->gateway/client path.  
**When to use:** Before publishing and before consuming at each hop.

**Key rule:** dedupe must be shared/distributed for multi-instance behavior; process-local sets are insufficient.

### Pattern 6: Resume-Anchor Compatible Migration

**What:** During owner migration, freeze old owner and continue on new owner starting from `resume_anchor` semantics from Phase 6.  
**When to use:** explicit switch or timeout-driven failover.

**Key rule:** no normal continuation on new owner before anchor convergence and fence activation.

### Anti-Patterns to Avoid

- Updating route owner and fence in separate non-atomic calls.
- Using Redis Pub/Sub for guaranteed relay.
- Keeping dedupe/owner truth in per-process memory only.
- Emitting high-cardinality metric tags (`trace_id`, `session_id`) into counters/timers.
- Mixing timestamp and numeric `route_version` schemes in different services.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| Cross-instance guaranteed relay | Custom in-memory queue + Pub/Sub fallback | Redis Streams consumer groups with ack/pending/reclaim | Provides replay and pending-entry recovery needed for at-least-once |
| Owner migration correctness | Two-step read-then-write in app code | Atomic Lua CAS update | Prevents split-brain owner states |
| Pending message takeover | Manual scan-and-requeue logic only | `XPENDING` + `XAUTOCLAIM` | Standard reclaim path after consumer failure |
| Delivery retries | Infinite backoff loops | Window-bounded retries + deterministic terminal envelope | Matches locked 15-minute replay window and failure semantics |
| Duplicate suppression | Local `Set` only | Distributed dedupe key store with TTL | Survives process restarts and multi-instance delivery |

**Key insight:** Phase 8 is primarily a correctness phase. Favor proven Redis primitives and existing project semantics over custom queue/distributed-lock inventions.

## Common Pitfalls

### Pitfall 1: Split-Brain Owner After Migration
**What goes wrong:** Two instances accept the same session after migration race.  
**Why it happens:** Non-atomic route/fence mutation.  
**How to avoid:** Single Lua CAS script mutates owner + version + fence together.  
**Warning signs:** Interleaved logs from two owner instances for same `session_id`.

### Pitfall 2: Relay Message Loss with Pub/Sub
**What goes wrong:** Consumer restarts miss messages permanently.  
**Why it happens:** Pub/Sub at-most-once semantics.  
**How to avoid:** Use Streams with consumer groups + pending recovery.  
**Warning signs:** Gap in `seq` without corresponding pending entries.

### Pitfall 3: CROSSSLOT Errors in Redis Cluster
**What goes wrong:** CAS scripts or multi-key operations fail in cluster mode.  
**Why it happens:** Keys mapped to different hash slots.  
**How to avoid:** Use key hash-tags for same-session multi-key operations.  
**Warning signs:** Runtime `CROSSSLOT` errors during routing updates.

### Pitfall 4: Consumer Offset Misconfiguration
**What goes wrong:** Stream messages silently skipped after restart.  
**Why it happens:** Inappropriate read-offset strategy (for example, always reading latest).  
**How to avoid:** Use consumer-group offsets intentionally and test restart semantics.  
**Warning signs:** Missing expected relays with no failure logs.

### Pitfall 5: Duplicate Commands on Redis Reconnect
**What goes wrong:** A write may execute more than once around reconnect windows.  
**Why it happens:** Client reconnect/retry behavior with at-least-once command execution patterns.  
**How to avoid:** Enforce idempotency keys and side-effect guards for route/relay writes.  
**Warning signs:** Same dedupe tuple observed with different stream IDs close in time.

### Pitfall 6: Unbounded Stream Growth
**What goes wrong:** Redis memory pressure and degraded read latency.  
**Why it happens:** Streams never trimmed, or trimming without replay-window alignment.  
**How to avoid:** Trim streams with retention policy tied to replay window and capacity plan.  
**Warning signs:** Stream length growth without stable plateau.

### Pitfall 7: Non-Deterministic Failure Envelope
**What goes wrong:** Upstream cannot choose correct retry/restart action.  
**Why it happens:** Missing `route_version` or inconsistent `error_code/next_action` population.  
**How to avoid:** Contract tests for every terminal branch and timeout branch.  
**Warning signs:** Partial error payloads in logs or API responses.

## Code Examples

Verified patterns adapted to this project context:

### Example 1: Route CAS + Fence (Lua, atomic)

```lua
-- KEYS[1] route key, ARGV[1] expected_version, ARGV[2] new_version
-- ARGV[3] new_skill_owner, ARGV[4] new_gateway_owner, ARGV[5] fenced_owner, ARGV[6] ttl_seconds
local current = redis.call('HGET', KEYS[1], 'route_version')
if current ~= false and tonumber(current) ~= tonumber(ARGV[1]) then
  return {err = 'ROUTE_VERSION_CONFLICT'}
end
redis.call('HSET', KEYS[1],
  'route_version', ARGV[2],
  'skill_owner_instance_id', ARGV[3],
  'gateway_owner_instance_id', ARGV[4],
  'fenced_owner_instance_id', ARGV[5],
  'updated_at_epoch_ms', tostring(redis.call('TIME')[1] * 1000))
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[6]))
return 'OK'
```

### Example 2: Stream Consume + Ack + Reclaim Skeleton (Java)

```java
// Pseudocode for gateway/skill-service relay workers
List<MapRecord<String, String, String>> records = redis.xreadgroup(
    Consumer.from(group, consumer),
    StreamReadOptions.empty().count(32).block(Duration.ofSeconds(1)),
    StreamOffset.create(streamKey, ReadOffset.lastConsumed()));

for (MapRecord<String, String, String> record : records) {
    if (dedupeGuard.seen(record.getValue().get("dedupe_key"))) {
        redis.xack(streamKey, group, record.getId());
        continue;
    }
    processRelay(record);
    redis.xack(streamKey, group, record.getId());
}

// Periodic reclaim for stale pending entries
redis.xautoclaim(streamKey, Consumer.from(group, consumer), Duration.ofSeconds(30), "0-0");
```

### Example 3: Deterministic Terminal Envelope

```json
{
  "error_code": "ROUTE_REPLAY_WINDOW_EXPIRED",
  "next_action": "restart_session",
  "trace_id": "trace-123",
  "route_version": 42,
  "reason_code": "OWNER_UNKNOWN_TIMEOUT"
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|---|---|---|---|
| In-process owner map (`ResumeCoordinator` local map) | Redis route truth + CAS/fence | Phase 8 target | Enables correctness across multi-instance deployment |
| Fire-and-forget relay assumptions | Stream consumer-group relay with ack/pending/reclaim | Phase 8 target | Supports replay and failover recovery semantics |
| Local duplicate suppression only | Distributed dedupe tuple key across hops | Phase 8 target | Prevents duplicate/ghost writes after retries and failover |
| Manual pending claim patterns | `XAUTOCLAIM` support in Redis (`>= 6.2`) | Redis 6.2+ | Simplifies pending recovery implementation |
| Manual stream idempotency handling only | Optional stream-level idempotency (`XADD ... IDMP`) | Redis 8.6+ | Can simplify producer dedupe when infra supports 8.6+, otherwise keep app-level dedupe |

**Deprecated/outdated for this phase:**

- Redis Pub/Sub as primary guaranteed delivery path for cross-instance relay (insufficient delivery semantics).

## Open Questions

1. **Event bus implementation boundary**
   - What we know: "event bus" is locked, route/compensation are Redis-anchored.
   - What's unclear: whether to standardize on Redis Streams only, or integrate existing enterprise Kafka/MQ.
   - Recommendation: decide in Wave 0; default to Redis Streams to minimize scope unless enterprise MQ is mandatory.

2. **Redis deployment mode**
   - What we know: keys and streams will be central to phase correctness.
   - What's unclear: standalone/sentinel/cluster mode in target environments.
   - Recommendation: lock deployment mode before key naming and script design; cluster mode requires hash-tag strategy.

3. **`route_version` encoding**
   - What we know: field is mandatory and used in conflict handling.
   - What's unclear: numeric monotonic version vs timestamp version.
   - Recommendation: use monotonic numeric version for deterministic CAS retries.

4. **Two-stage ack timeout budgets**
   - What we know: stage 2 requires timeout status and deterministic failure branch.
   - What's unclear: exact timeout/retry thresholds.
   - Recommendation: define default SLO-based thresholds in Wave 0 and expose as config.

5. **Requirement IDs**
   - What we know: roadmap lists `TBD`.
   - What's unclear: final ID schema and mapping to acceptance criteria.
   - Recommendation: create provisional `P08-*` IDs at plan start and use them in every plan/test artifact.

## Sources

### Primary (HIGH confidence, repo-local)

- `.planning/phases/08-ai-gateway-skill-service-opencode/08-CONTEXT.md`
- `.planning/STATE.md`
- `.planning/ROADMAP.md`
- `.planning/milestones/v1.0-REQUIREMENTS.md`
- `.planning/config.json`
- `gateway/src/main/java/com/chatcui/gateway/runtime/ResumeCoordinator.java`
- `gateway/src/main/java/com/chatcui/gateway/runtime/ResumeDecision.java`
- `gateway/src/main/java/com/chatcui/gateway/runtime/BridgePersistencePublisher.java`
- `gateway/src/main/java/com/chatcui/gateway/persistence/SkillPersistenceForwarder.java`
- `gateway/src/main/java/com/chatcui/gateway/persistence/DeliveryRetryQueue.java`
- `gateway/src/main/java/com/chatcui/gateway/observability/BridgeMetricsRegistry.java`
- `skill-service/src/main/java/com/chatcui/skill/service/TurnPersistenceService.java`
- `skill-service/src/main/java/com/chatcui/skill/service/SendbackService.java`
- `skill-service/src/main/java/com/chatcui/skill/observability/SkillMetricsRecorder.java`
- `pc-agent-plugin/src/core/runtime/BridgeRuntime.ts`

### Secondary (MEDIUM-HIGH confidence, official docs)

- Redis Pub/Sub semantics (at-most-once): https://redis.io/docs/latest/develop/pubsub/
- Redis Streams intro and consumer-groups: https://redis.io/docs/latest/develop/data-types/streams/
- `XREADGROUP`: https://redis.io/docs/latest/commands/xreadgroup/
- `XAUTOCLAIM` (`>= 6.2.0`): https://redis.io/docs/latest/commands/xautoclaim/
- `XTRIM` retention controls: https://redis.io/docs/latest/commands/xtrim/
- Redis transactions (`WATCH`/`MULTI`/`EXEC`): https://redis.io/docs/latest/develop/using-commands/transactions/
- Redis `SET` options (`NX`/`XX`/`EX`/`PX`): https://redis.io/docs/latest/commands/set/
- Redis cluster multi-key and hash-tag constraints (`CROSSSLOT`): https://redis.io/docs/latest/develop/using-commands/keyspace/
- Spring Data Redis Stream listener API cautions (offset/ack/error behavior): https://docs.spring.io/spring-data/redis/docs/3.2.12/api/org/springframework/data/redis/stream/StreamMessageListenerContainer.html
- Lettuce reconnect/at-least-once execution notes: https://redis.github.io/lettuce/advanced-usage/

### Tertiary (LOW confidence, validate before commitment)

- Redis stream producer idempotency option (`XADD ... IDMP`) in Redis 8.6 docs section:
  https://redis.io/docs/latest/develop/data-types/streams/

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH (strongly constrained by locked decisions + existing module stack)
- Architecture patterns: MEDIUM-HIGH (repo-local baseline + official Redis semantics)
- Pitfalls: MEDIUM-HIGH (official docs + distributed systems failure modes)

**Research date:** 2026-03-04  
**Valid until:** 2026-04-03 (revalidate if Redis version/infra decision changes)
