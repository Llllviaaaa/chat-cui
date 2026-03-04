# Phase 8: ai-gateway-skill-service-opencode - Context

**Gathered:** 2026-03-04
**Status:** Ready for planning

<domain>
## Phase Boundary

本阶段仅解决以下固定问题：

- 在 `ai-gateway` 与 `skill-service` 均为多实例部署时，确保 OpenCode 消息可以精准投递到目标客户端用户。
- 处理跨实例链路下的 owner 归属、迁移切换、异常补偿、可观测确认语义，避免错投、重投、乱序和幽灵写入。

本阶段不包含：

- 新业务能力扩展（如新 UI、新产品特性、权限模型重构）。
- 跨地域部署、成本优化、独立的新监控平台建设。

</domain>

<decisions>
## Implementation Decisions

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

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `gateway/src/main/java/com/chatcui/gateway/runtime/ResumeCoordinator.java`
  - 已有 owner 冲突与 resume anchor 判定逻辑，可扩展为跨实例 owner 决策内核。
- `gateway/src/main/java/com/chatcui/gateway/runtime/BridgePersistencePublisher.java`
  - 已覆盖 `continue/drop_duplicate/compensate_gap/terminal_failure` 分支，适合作为路由决策后的统一持久化/补偿入口。
- `gateway/src/main/java/com/chatcui/gateway/persistence/SkillPersistenceForwarder.java`
  - 已具备异步投递、失败重试入队、结构化失败包能力，可复用为跨实例 relay 后的落库/转发器。
- `gateway/src/main/java/com/chatcui/gateway/persistence/DeliveryRetryQueue.java`
  - 已有重试队列与状态上报机制，可映射到“未知 owner 短暂排队 + 重查”策略。
- `skill-service/src/main/java/com/chatcui/skill/service/TurnPersistenceService.java`
  - 已实现去重与序列单调保护，可承接跨 hop 至少一次语义下的幂等落库。
- `pc-agent-plugin/src/core/runtime/BridgeRuntime.ts`
  - 已具备 `resume_anchor`、补偿触发、重连状态事件，可保持端侧与服务侧语义一致。

### Established Patterns
- 跨服务核心字段已统一：`tenant_id`, `client_id`, `session_id`, `turn_id`, `seq`, `trace_id`。
- 事件与 API 合约使用 `snake_case`。
- 失败语义采用确定性 `reason_code/next_action` 模式（Phase 6 已建立）。
- 单会话单 owner 语义与 owner 冲突终止分支已在现有逻辑与测试中出现，可直接继承。

### Integration Points
- 网关路由决策层：在 `ResumeCoordinator` 之上增加“跨实例 owner 路由与 CAS 迁移”能力。
- 网关投递层：在 `BridgePersistencePublisher` 前插入“按路由目标进行 relay”步骤。
- skill-service 转发层：补充接收 relay 事件并按完整路由目标下发到目标 gateway/client。
- 观测链路：复用现有 `trace_id` 与 bridge metrics，增加路由版本与阶段确认标签。

</code_context>

<specifics>
## Specific Ideas

- 你的核心场景已经明确：客户端链路位于 `skillserviceA -> gatewayB`，但 OpenCode 长连接位于 `gatewayA`，系统必须支持跨实例桥接，不依赖“所有连接同实例”假设。
- 你倾向于优先保证“精准投递和一致性”，再追求实现简化，因此多项决策选择了有明确 fence 和确认语义的方案。

</specifics>

<deferred>
## Deferred Ideas

- 多租户限流策略细粒度治理（如每租户每会话速率控制）可在后续 phase 单独展开。
- 告警阈值和运营看板细化可在后续 observability phase 独立建设。

</deferred>

---

*Phase: 08-ai-gateway-skill-service-opencode*
*Context gathered: 2026-03-04*
