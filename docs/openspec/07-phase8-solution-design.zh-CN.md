---
spec_standard: OpenSpec
spec_type: solution
spec_id: OSS-CHATCUI-PHASE8
title: Chat CUI Phase 8 分布式精确投递方案设计文档
version: 1.0.0
status: draft
owner: ChatCUI Delivery Team
created_at: 2026-03-04
updated_at: 2026-03-04
language: zh-CN
---

# SOL-1. 方案目标

将 Phase 8 技术目标落地为可实现方案，并明确“实现边界、算法策略、数据契约、性能与回滚策略”。

- 目标需求：`P08-ROUTE-01` ~ `P08-OBS-01`
- 交付方式：增量演进，不破坏既有 Phase 1~7 行为

# SOL-2. 分层方案

| 分层 | 方案要点 | 产出 |
|---|---|---|
| 路由层 | Redis Hash + Lua CAS 管理 owner pair | `RouteOwnershipRecord` `RedisRouteOwnershipStore` |
| 转发层 | gateway 首跳发布 + skill owner 消费转发 | `RelayEnvelope` `RedisRelayPublisher` `RelayEventConsumer` |
| 一致性层 | 统一 dedupe tuple + ACK 状态机 | `DeliveryAckStateMachine` |
| 恢复层 | route 缺失短暂重查 + 15 分钟终止 | `UnknownOwnerRecoveryWorker` |
| 可观测层 | 指标低基数 + 结构化日志 | `BridgeMetricsRegistry` `SkillMetricsRecorder` |

# SOL-3. 数据契约方案

## SOL-3.1 RouteOwnershipRecord（Redis Hash）

| 字段 | 类型 | 说明 |
|---|---|---|
| `tenant_id` | string | 租户 ID |
| `session_id` | string | 会话 ID |
| `route_version` | long | 路由版本号 |
| `skill_owner` | string | skill-service owner |
| `gateway_owner` | string | gateway owner |
| `fenced_owner` | string/null | 被 fence 的旧 owner |
| `updated_at_epoch_ms` | long | 毫秒级更新时间 |

## SOL-3.2 RelayEnvelope（首跳消息）

必须包含如下字段：

- `tenant_id/client_id/session_id/turn_id/seq/topic/trace_id`
- `route_version/source_gateway_owner/target_skill_owner/target_gateway_owner`
- `hop/partition_key/dedupe_key/actor/event_type/payload`
- 可选：`reason_code/next_action`

## SOL-3.3 Dedupe Key 规则

固定公式：

```text
dedupe_key = session_id + "|" + turn_id + "|" + seq + "|" + topic
```

# SOL-4. 关键流程方案

## SOL-4.1 非目标 gateway owner 首跳发布

1. 加载 route 记录。
2. 判断本地 `gateway_owner_id` 是否与 route 的 `gateway_owner` 一致。
3. 不一致时构造 `RelayEnvelope`。
4. 执行 `SET NX + EX` 去重。
5. 去重通过后 `XADD` 到 `chatcui:relay:first-hop:{tenant:session}`。

## SOL-4.2 skill owner 消费与二跳分发

1. consumer 读取 stream 记录。
2. 先做 tuple 去重。
3. 路由缺失则进入 pending/recovery 分支。
4. 非本 skill owner 则 `SKIPPED_NOT_OWNER` 并 ACK。
5. 本 owner 则向目标 gateway owner 分发，成功后 ACK。

## SOL-4.3 ACK 状态机

状态集合：

- `gateway_owner_accepted`
- `client_delivered`
- `client_delivery_timeout`

状态转移原则：

1. 可以从空状态直接进入 `gateway_owner_accepted`。
2. 成功投递后进入 `client_delivered`。
3. 失败进入 `client_delivery_timeout`，并固化 `error_code/next_action`。
4. 一旦 timeout，不允许后续 delivered 覆盖。

## SOL-4.4 未知 owner 恢复

1. route 缺失时记录 `first_seen_at`。
2. 在 15 分钟内允许重查并重试。
3. 超过 15 分钟返回 `ROUTE_REPLAY_WINDOW_EXPIRED`，终止重试。

# SOL-5. 算法与伪代码

## SOL-5.1 CAS 迁移算法

```text
input: tenant_id, session_id, expected_route_version, new_skill_owner, new_gateway_owner, fenced_owner
load current route by redis hash
if route missing -> return MISSING
if current.route_version != expected_route_version -> return VERSION_CONFLICT(current)
next_version = current.route_version + 1
write tenant_id, session_id, next_version, new owners, fenced_owner, updated_at
set TTL
return APPLIED(updated_route)
```

## SOL-5.2 首跳发布算法

```text
input: event, route, local_gateway_owner
if route.gateway_owner == local_gateway_owner:
  local forward and mark client_delivered
else:
  envelope = firstHop(event, route, local_gateway_owner)
  if dedupe NX failed: mark duplicate suppressed and finish
  xadd stream(envelope)
  mark client_delivered
```

## SOL-5.3 恢复算法

```text
input: recovery_entry(now, first_seen_at)
if now >= first_seen_at + replay_window:
  return REPLAY_WINDOW_EXPIRED(error_code=ROUTE_REPLAY_WINDOW_EXPIRED)
route = load_route(tenant_id, session_id)
if route missing:
  return PENDING_ROUTE_RECHECK(next_action=retry_via_route_recheck)
dispatch with route
return RETRIED(route_version=route.route_version)
```

# SOL-6. 缓存与性能方案

## SOL-6.1 缓存/Key 策略

| Key | 示例 | TTL |
|---|---|---|
| route key | `chatcui:route:{tenant-a:session-a}` | 路由生命周期决定 |
| relay stream | `chatcui:relay:first-hop:{tenant-a:session-a}` | 按 stream 保留策略 |
| dedupe key | `chatcui:relay:dedupe:{tenant-a:session-a}||session-a|turn-1|1|skill.turn.delta` | 15 分钟 |

## SOL-6.2 性能优化手段

1. route 迁移使用 Lua 脚本原子执行，减少网络往返。
2. relay 与本地 forward 分支早判断，避免不必要序列化。
3. 去重前置，降低重复写 stream 与重复分发成本。
4. 指标限制低基数标签，避免监控系统高基数爆炸。

# SOL-7. 回滚与故障处理

| 故障类型 | 处理方案 |
|---|---|
| route 冲突频繁 | 临时冻结迁移入口，检查版本竞争来源，开启冲突告警 |
| relay 发布异常 | 标记 `client_delivery_timeout`，触发 `retry_via_route_recheck` |
| skill owner 不可达 | 消费侧返回 `PENDING_RETRY`，进入恢复窗口 |
| 恢复超窗 | 返回 `ROUTE_REPLAY_WINDOW_EXPIRED`，阻断无限重试 |

# SOL-8. 交付与验证策略

推荐执行顺序：

1. 路由层与 CAS 单测
2. relay 发布/消费链路单测 + 集成测试
3. ACK 与恢复测试
4. 指标与日志可观测验证

验证命令可参考：

- `.\mvnw.cmd -pl gateway "-Dtest=RouteKeyFactoryTest,RedisRouteOwnershipStoreTest" test`
- `.\mvnw.cmd -pl "gateway,skill-service" "-Dtest=BridgePersistencePublisherTest,CrossInstanceRelayIntegrationTest" test`
- `.\mvnw.cmd -pl "gateway,skill-service" "-Dtest=DeliveryAckStateMachineTest,DeliveryRetryQueueTest,ResumeCoordinatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

# SOL-9. 关联文档

- 需求文档：`docs/openspec/05-phase8-requirements-design.zh-CN.md`
- 架构文档：`docs/openspec/06-phase8-architecture-design.zh-CN.md`
- 测试文档：`docs/openspec/08-phase8-test-cases.zh-CN.md`
- 详细设计：`docs/phase8/phase8-detailed-design.zh-CN.md`

