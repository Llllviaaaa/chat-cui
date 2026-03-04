---
spec_standard: OpenSpec
spec_type: requirements
spec_id: OSR-CHATCUI-PHASE8
title: Chat CUI Phase 8 分布式精确投递需求规格说明书
version: 1.0.0
status: draft
owner: ChatCUI Product & Platform Team
created_at: 2026-03-04
updated_at: 2026-03-04
language: zh-CN
---

# 1. 文档目标

将 Phase 8 的核心问题从“实现经验”沉淀为“可审查、可追踪、可验收”的正式需求规格：

- 在 `ai-gateway` 与 `skill-service` 多实例部署时，保证 OpenCode 消息精确投递到正确客户端会话。
- 在 owner 迁移、跨实例转发、异常重试场景下，保证一致性与可解释性。
- 用统一需求编号（`P08-*`）建立需求到实现到测试的闭环。

# 2. 项目范围

## 2.1 In Scope

- 路由真相源与 owner 归属模型（`P08-ROUTE-01`）
- owner 迁移与 fencing 机制（`P08-FENCE-01`）
- gateway 到 skill owner 再到目标 gateway/client 的跨实例 relay（`P08-RELAY-01`）
- 全链路去重元组（`P08-DEDUPE-01`）
- 两阶段投递确认（`P08-ACK-01`）
- 未知 owner 的有界恢复（`P08-RECOVERY-01`）
- 路由/转发/确认/恢复的可观测闭环（`P08-OBS-01`）

## 2.2 Out of Scope

- 新业务能力（新 UI、新付费能力、新权限模型重构）
- 跨地域多活与成本治理
- 外部监控平台改造

# 3. 核心业务流程（Mermaid）

```mermaid
flowchart TD
    A[OpenCode 消息进入 gatewayA] --> B{gatewayA 是否为会话目标 gateway owner}
    B -->|是| C[本地写入/下发]
    B -->|否| D[构造 RelayEnvelope 首跳消息]
    D --> E[写入 Redis Stream<br/>chatcui:relay:first-hop:{tenant:session}]
    E --> F[skill-service owner 消费消息]
    F --> G{路由是否存在且本实例是 skill owner}
    G -->|否 owner| H[返回 OWNER_FENCED 语义并 ACK]
    G -->|路由缺失| I[进入未知 owner 恢复队列]
    G -->|是| J[转发到 route 指定 gateway owner]
    J --> K[gatewayB 下发 client]
    C --> L[ACK 阶段1 gateway_owner_accepted]
    K --> M[ACK 阶段2 client_delivered/client_delivery_timeout]
    I --> N{15 分钟回放窗内?}
    N -->|是| O[重查路由并重投]
    N -->|否| P[终态: ROUTE_REPLAY_WINDOW_EXPIRED]
```

# 4. 角色分析与痛点

| 角色 | 职责 | 核心痛点 | 目标收益 |
|---|---|---|---|
| 终端用户（C 端） | 接收 AI 结果并继续会话 | 多实例切换时偶发收不到消息或重复消息 | 消息“只到我会话、状态可解释” |
| 网关开发/维护（B 端） | 维护 gateway 集群与路由策略 | owner 冲突与跨实例转发行为不可预测 | owner 归属可判定、冲突可诊断 |
| skill-service 维护者（B 端） | 维护 relay 消费与二跳转发 | 重复消费、未知 owner 重试失控 | 有界重放、去重可控、错误可终态化 |
| SRE/可观测负责人（B 端） | 监控与故障定位 | 只有日志无统一指标，跨服务排障成本高 | 低基数指标 + 统一阶段日志 |

# 5. 功能拆解（字段级规则 + 异常闭环）

| 功能模块 | 子功能 | 详细逻辑描述 | 前置条件 | 异常流程处理 |
|---|---|---|---|---|
| 路由归属 | Redis 路由真相源 | 路由键按 `tenant_id + session_id` 建模，记录 `route_version/skill_owner/gateway_owner/fenced_owner` | Redis 可用；会话已建立 | 路由不存在返回 `MISSING`；版本冲突返回 `VERSION_CONFLICT` |
| 路由归属 | CAS 迁移 | 迁移必须通过 `expected_route_version` CAS，成功后 `route_version+1` 并写入 `fenced_owner` | 持有期望版本 | 冲突时不得覆盖，返回当前版本供上游重试 |
| Fencing | 旧 owner 拒绝 | stale owner 收到消息必须返回 `OWNER_FENCED`，并带 `route_version` 诊断 | route 中存在 `fenced_owner` | 统一走终态，不允许静默吞掉 |
| 跨实例 relay | 首跳发布 | 非目标 gateway owner 生成 `RelayEnvelope`，写入 `chatcui:relay:first-hop:{tenant:session}` | 可读取 route；relay publisher 可用 | 发布失败进入 `client_delivery_timeout` 分支并给出下一步动作 |
| 跨实例 relay | skill owner 消费 | skill owner 从 stream 消费并按 route 转发到目标 gateway owner | 本实例具备 `local_skill_owner` 标识 | 非 owner 返回 `SKIPPED_NOT_OWNER` 并映射为 `OWNER_FENCED` |
| 去重 | 发布侧去重 | `dedupe_key = session_id|turn_id|seq|topic`，Redis `SET NX + TTL` | dedupe key 可生成 | NX 失败标记重复，不再重复投递 |
| 去重 | 消费侧去重 | 消费前 `markIfAbsent`；重复立即 ACK 并打点 `duplicate_dropped` | consumer group 正常 | 处于待重试状态时释放 dedupe 键，允许后续重放 |
| ACK 语义 | 两阶段确认 | 阶段 1 固定为 `gateway_owner_accepted`；阶段 2 为 `client_delivered` 或 `client_delivery_timeout` | 可识别会话 tuple | timeout 后再收到 delivered 不可回退终态 |
| 恢复 | 未知 owner 恢复队列 | route 缺失时不立即失败，进入短暂恢复队列并重查 route | 可记录 `first_seen_at` | 超过 15 分钟回放窗返回 `ROUTE_REPLAY_WINDOW_EXPIRED` |
| 可观测 | 指标与结构化日志 | 指标统一 `component/failure_class/outcome/retryable`；日志必须带 `trace_id/route_version/session_id/turn_id/seq/topic` | metric registry 与日志链路可用 | 禁止高基数标签进入 metric；异常必须可关联 trace |

# 6. 业务规则（无死角约束）

1. 同一 `tenant_id + session_id` 任意时刻只能有一个有效 gateway owner。
2. 只有 CAS 成功后 owner 迁移才生效，且必须更新 `route_version`。
3. 任意链路重复消息必须被 `session_id|turn_id|seq|topic` 去重。
4. ACK 状态机是单向的，`client_delivery_timeout` 与 `client_delivered` 均为终态，不允许回退。
5. 未知 owner 恢复窗口固定为 15 分钟，超窗必须终态失败，不允许无限重试。
6. 所有终态失败必须包含 `error_code`、`next_action`、`trace_id`、`route_version`（可获得时）。
7. 可观测标签必须低基数，`trace_id/session_id/turn_id` 只能进入日志，不可进入 metric tag。

# 7. 非功能性需求

## 7.1 性能指标

| 指标ID | 指标 | 目标 |
|---|---|---|
| P08-NFR-001 | route CAS 读写 | P95 < 30ms |
| P08-NFR-002 | 首跳 relay 发布 | P95 < 50ms |
| P08-NFR-003 | skill owner 消费到目标网关分发 | P95 < 120ms |
| P08-NFR-004 | owner 迁移生效延迟 | < 1s |

## 7.2 安全要求

| 指标ID | 要求 | 说明 |
|---|---|---|
| P08-SEC-001 | owner 归属不可伪造 | 路由迁移只允许 CAS API，拒绝无版本更新 |
| P08-SEC-002 | 失败信息脱敏 | 错误响应与日志不输出敏感凭证 |
| P08-SEC-003 | 访问隔离 | route key、stream key 必须带租户隔离维度 |

## 7.3 数据一致性要求

| 指标ID | 要求 | 说明 |
|---|---|---|
| P08-CONS-001 | 单会话单 owner 一致性 | route 表为唯一真相源 |
| P08-CONS-002 | 跨 hop 幂等一致性 | dedupe tuple 统一算法 |
| P08-CONS-003 | ACK 终态一致性 | timeout 与 delivered 不得相互覆盖 |
| P08-CONS-004 | 恢复边界一致性 | replay window 过期后必须终止 |

# 8. 需求追踪矩阵

| 需求ID | 需求说明 | 主实现位置 | 验证证据 |
|---|---|---|---|
| `P08-ROUTE-01` | Redis 路由真相源 + route_version | `gateway/.../RouteOwnershipRecord.java` `gateway/.../RedisRouteOwnershipStore.java` | `.planning/.../08-VERIFICATION.md` |
| `P08-FENCE-01` | fencing 与 `OWNER_FENCED` 拒绝 | `gateway/.../ResumeDecision.java` `gateway/.../BridgePersistencePublisher.java` | `.planning/.../08-VERIFICATION.md` |
| `P08-RELAY-01` | 首跳 relay + skill owner 消费分发 | `gateway/.../RedisRelayPublisher.java` `skill-service/.../RelayEventConsumer.java` | `.planning/.../08-VERIFICATION.md` |
| `P08-DEDUPE-01` | 全链路去重 tuple | `gateway/.../RelayEnvelope.java` `skill-service/.../RelayEventConsumer.java` | `.planning/.../08-VERIFICATION.md` |
| `P08-ACK-01` | 两阶段 ACK 状态机 | `gateway/.../DeliveryAckStateMachine.java` | `.planning/.../08-VERIFICATION.md` |
| `P08-RECOVERY-01` | 15 分钟有界恢复 | `gateway/.../UnknownOwnerRecoveryWorker.java` | `.planning/.../08-VERIFICATION.md` |
| `P08-OBS-01` | 路由/转发/确认/恢复可观测 | `gateway/.../BridgeMetricsRegistry.java` `skill-service/.../SkillMetricsRecorder.java` | `.planning/.../08-OBSERVABILITY-BASELINE.md` |

