# Phase 6: Reliability + Observability Hardening - Context

**Gathered:** 2026-03-04
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase hardens reliability and diagnostics for existing v1 flow, scoped to `BRG-04` and `DEM-02` only:

- transient disconnect recovery behavior for plugin <-> gateway path
- session resume continuity with ordering and duplicate prevention
- cross-service traceability and failure-class observability

It does not add new user-facing product capabilities beyond reliability/observability hardening.

</domain>

<decisions>
## Implementation Decisions

### Connection Recovery Policy
- Default policy is automatic reconnect with bounded attempts, not immediate fail-fast.
- Retry pacing uses exponential backoff with jitter.
- Every reconnect attempt must perform fresh AK/SK authentication before resume.
- Runtime surfaces phased recovery states to upper layers: `reconnecting`, `resumed`, `failed`.
- Terminal failure payload must include deterministic reason code and `next_action`.

### Session Continuity and Duplicate Control
- Resume anchor is last acknowledged tuple: `session_id + turn_id + seq`.
- On sequence anomalies after reconnect:
  - duplicate `seq` events are dropped with diagnostic records
  - gap events trigger compensation/replay before normal continuation
- Same session uses a single-owner reconnect flow (no concurrent multi-owner resume).
- Sendback must use server-side idempotency keys (session context + content fingerprint) to block duplicate IM sends during reconnect/retry windows.

### Observability Baseline and Failure Taxonomy
- One `trace_id` is propagated end-to-end across plugin, gateway, and skill-service boundaries.
- Structured logs use a shared minimum field set:
  `tenant_id`, `client_id`, `session_id`, `turn_id`, `seq`, `trace_id`, `error_code`, `component`, `status`.
- Cross-service failure taxonomy is unified as:
  `auth`, `bridge`, `persistence`, `sendback`, `unknown`.
- Every failure class carries retry semantics (`retryable` vs `terminal`).
- Delivery mode is dual-track: structured logs for trace drill-down + metrics for dashboard/alerting.

### Claude's Discretion
- Exact reconnect max-attempt and backoff cap values.
- Jitter formula and reconnect cooldown tuning.
- Idempotency key hashing algorithm and idempotency window TTL.
- Exact metric names, labels, and dashboard layout details.

</decisions>

<specifics>
## Specific Ideas

- Keep user experience resilient first: transient disconnects should recover automatically when safe.
- Reliability hardening must preserve replay correctness and sendback integrity over raw reconnect speed.
- Diagnostics must support fast cross-service triage without exposing sensitive payload content.

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `pc-agent-plugin/src/core/runtime/BridgeRuntime.ts`
  - already has reconnect hooks (`onReconnect`), health/error events, in-flight turn guard, and `seq` anomaly detection.
- `pc-agent-plugin/src/host-adapter/HostEventBridge.ts`
  - already maps host/runtime events including `runtime.reconnect` and `runtime.health`.
- `gateway/src/main/java/com/chatcui/gateway/persistence/SkillPersistenceForwarder.java`
  - already deduplicates accepted tuples by `session_id|turn_id|seq` and supports async forwarding.
- `gateway/src/main/java/com/chatcui/gateway/persistence/DeliveryRetryQueue.java`
  - already provides bounded retry execution with pending/saved/failed transitions.
- `gateway/src/main/java/com/chatcui/gateway/persistence/DeliveryStatusReporter.java`
  - already tracks tuple delivery status lifecycle.
- `skill-service/src/main/java/com/chatcui/skill/service/TurnPersistenceService.java`
  - already enforces persistence-side tuple dedupe and monotonic `seq` guards.

### Established Patterns
- Protocol contracts and API payloads remain `snake_case`.
- Session metadata baseline is stable: `tenant_id`, `client_id`, `session_id`, `turn_id`, `seq`, `trace_id`.
- Error handling pattern is deterministic typed code/message (safe for UI and ops consumption).
- Active-session / in-flight turn constraints from previous phases remain in force.

### Integration Points
- Plugin runtime/host adapter event pipeline for reconnect state and health propagation to client UI.
- Gateway forwarder + retry queue for persistence path reliability and status visibility.
- Skill-service persistence/sendback path for resume correctness and duplicate sendback protection.
- Web demo status/error surfaces for exposing phased reconnect and deterministic failure diagnostics.

</code_context>

<deferred>
## Deferred Ideas

None - discussion stayed within Phase 6 scope.

</deferred>

---

*Phase: 06-reliability-observability-hardening*
*Context gathered: 2026-03-04*
