---
spec_standard: OpenSpec
spec_type: test-cases
spec_id: OST-CHATCUI-PHASE8
title: Chat CUI Phase 8 分布式精确投递测试用例文档
version: 1.0.0
status: draft
owner: ChatCUI QA Team
created_at: 2026-03-04
updated_at: 2026-03-04
language: zh-CN
---

# TST-1. 测试目标

覆盖 `P08-ROUTE-01` 到 `P08-OBS-01` 全需求，验证在多实例条件下：

- 消息投递路径正确
- owner 迁移冲突可收敛
- 重复消息不重复下发
- 异常恢复有边界且有终态
- 指标与日志可用于排障

# TST-2. 测试策略

## TST-2.1 功能测试

- route load/upsert/casTransfer 行为
- gateway 首跳 relay 与 skill owner 分发
- ACK 两阶段状态转移
- 恢复与 replay window 终态

## TST-2.2 边界值测试

- `route_version=0` 与大版本号
- `seq` 重复、乱序、跳号
- replay window 临界时刻（14:59 / 15:00）

## TST-2.3 异常中断测试

- route 缺失
- relay 发布异常
- dispatch 异常
- owner 迁移后 stale owner 输入

## TST-2.4 安全与一致性测试

- 租户隔离 key 规则
- 指标低基数标签检查
- 终态错误码与 next_action 完整性

# TST-3. 用例列表

| 用例ID | 模块 | 测试标题 | 预置条件 | 操作步骤 | 预期结果 | 优先级 |
|---|---|---|---|---|---|---|
| P08-TC-001 | Route | 路由记录创建成功 | Redis 可用 | 调用 `upsert` 写 route | 返回记录与输入一致 | P0 |
| P08-TC-002 | Route | CAS 迁移成功 | 已存在 route_version=12 | `casTransfer(expected=12)` | 返回 `APPLIED` 且版本=13 | P0 |
| P08-TC-003 | Route | CAS 版本冲突 | 已存在 route_version=12 | `casTransfer(expected=11)` | 返回 `VERSION_CONFLICT` 且 current=12 | P0 |
| P08-TC-004 | Route | 路由缺失返回 missing | route 不存在 | 调用 `casTransfer` | 返回 `MISSING` | P1 |
| P08-TC-005 | Fence | stale owner 被拒绝 | route 中 `fenced_owner=old` | old owner 发起 resume | 返回 `OWNER_FENCED` + `route_version` | P0 |
| P08-TC-006 | Relay | 非目标 gateway 触发首跳 relay | 本地 owner != route.gateway_owner | publish `skill.turn.delta` | 不走本地 forward，写入 relay envelope | P0 |
| P08-TC-007 | Relay | 目标 gateway 走本地 forward | 本地 owner == route.gateway_owner | publish `skill.turn.delta` | 本地下发且不写首跳 stream | P0 |
| P08-TC-008 | Dedupe | 发布侧重复抑制 | 同一 tuple 连续投递两次 | 连续 publish 两次 | 第二次被 duplicate 抑制 | P0 |
| P08-TC-009 | Dedupe | 消费侧重复抑制 | 同一 stream 逻辑消息被重复消费 | 连续 consume 两次 | 第二次 `DUPLICATE_DROPPED` 且 ACK | P0 |
| P08-TC-010 | ACK | ACK 阶段1生成 | 任意正常投递 | 调用 publish | 记录 `gateway_owner_accepted` | P0 |
| P08-TC-011 | ACK | ACK 阶段2成功 | 投递成功 | publish 完成 | 记录 `client_delivered` | P0 |
| P08-TC-012 | ACK | ACK timeout 终态固化 | 先 timeout 后 delivered | 先 `markClientDeliveryTimeout` 再 delivered | 状态保持 `client_delivery_timeout` | P0 |
| P08-TC-013 | Recovery | route 缺失进入待重试 | routeResolver 返回 empty | consume message | `PENDING_RETRY` 且不 ACK | P0 |
| P08-TC-014 | Recovery | 回放窗内恢复成功 | first_seen 在 15 分钟内 | worker.process | `RETRIED` 且带新 `route_version` | P0 |
| P08-TC-015 | Recovery | 回放窗超时终态 | first_seen 超过 15 分钟 | worker.process | `ROUTE_REPLAY_WINDOW_EXPIRED` + `restart_session` | P0 |
| P08-TC-016 | Relay/Fence | 非 skill owner 消费分支 | local_skill_owner != route.skill_owner | consume message | `SKIPPED_NOT_OWNER` 并 ACK | P0 |
| P08-TC-017 | Metrics | gateway 指标标签稳定 | 开启 meter registry | 执行 continue/drop/timeout 场景 | 标签仅 `component/failure_class/outcome/retryable` | P1 |
| P08-TC-018 | Metrics | skill relay 指标覆盖 | 开启 meter registry | 执行 success/timeout/fenced/expired | 指标计数符合场景 | P1 |
| P08-TC-019 | Log | 结构化日志字段完整 | 日志采集开启 | 执行 relay + ack + timeout | 日志含 `trace_id/route_version/session_id/turn_id/seq/topic` | P1 |
| P08-TC-020 | 端到端 | gatewayA->skillOwner->gatewayB 路径 | 构造 cross-instance route | 执行跨实例消息流程 | 消息落到 gatewayB 对应 client | P0 |

# TST-4. API 与状态码校验点

## TST-4.1 Skill-Service HTTP API

| 接口 | 场景 | 期望状态码 | 异常返回校验 |
|---|---|---|---|
| `POST /demo/skill/sessions/{session_id}/turns` | 正常创建 turn | `202` | 响应带 `trace_id/request_id` |
| `GET /sessions/{session_id}/history` | 参数合法 | `200` | 返回分页与历史结构 |
| `POST /sessions/{session_id}/sendback` | 正常回传 | `200` | 返回 IM 消息 ID 或请求 ID |
| 任意接口 | 参数缺失/格式错 | `400` | `error.code` 为参数类错误 |
| 任意接口 | 无权限（若接入鉴权） | `403` | `error.code` 可区分授权失败 |
| 任意接口 | 未处理异常 | `500` | `error.code` 稳定且有 trace 信息 |

## TST-4.2 内部语义码校验

| 场景 | 错误码 | next_action |
|---|---|---|
| stale owner 被 fence | `OWNER_FENCED` | `reroute_to_active_owner` |
| 恢复超出回放窗 | `ROUTE_REPLAY_WINDOW_EXPIRED` | `restart_session` |
| relay 发布超时 | `RELAY_CLIENT_DELIVERY_TIMEOUT` | `retry_via_route_recheck` |

# TST-5. 覆盖率声明

需求覆盖映射：

- `P08-ROUTE-01`: `P08-TC-001~004`
- `P08-FENCE-01`: `P08-TC-005`, `P08-TC-016`
- `P08-RELAY-01`: `P08-TC-006`, `P08-TC-007`, `P08-TC-020`
- `P08-DEDUPE-01`: `P08-TC-008`, `P08-TC-009`
- `P08-ACK-01`: `P08-TC-010~012`
- `P08-RECOVERY-01`: `P08-TC-013~015`
- `P08-OBS-01`: `P08-TC-017~019`

覆盖结论：功能、边界、异常中断、安全与可观测均已覆盖，满足 Phase 8 交付验收要求。

# TST-6. 推荐自动化命令

- `.\mvnw.cmd -pl gateway "-Dtest=RouteKeyFactoryTest,RedisRouteOwnershipStoreTest" test`
- `.\mvnw.cmd -pl "gateway,skill-service" "-Dtest=BridgePersistencePublisherTest,CrossInstanceRelayIntegrationTest" test`
- `.\mvnw.cmd -pl "gateway,skill-service" "-Dtest=DeliveryAckStateMachineTest,DeliveryRetryQueueTest,ResumeCoordinatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

