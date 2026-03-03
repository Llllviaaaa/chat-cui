# Phase 3: Skill Service Persistence APIs - Context

**Gathered:** 2026-03-04
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase establishes Skill service as the source of truth for session history persistence and retrieval APIs.

Scope is limited to:
- gateway forwarding semantics into Skill persistence path
- Skill-side conversation turn persistence with required metadata
- history query API contract with baseline pagination

It does not include UI interaction behavior (Phase 4), IM sendback API behavior (Phase 5), or reliability hardening across reconnect classes (Phase 6).

</domain>

<decisions>
## Implementation Decisions

### Carried Forward (Locked from prior phases)
- Plugin-first architecture remains fixed; PC agent bridge contract is unchanged.
- Stream contract remains `delta + final + completed` with `session_id`, `turn_id`, `seq`, `trace_id` and `snake_case` fields.
- Session runtime keeps one in-flight turn policy and structured error event pattern.

### Conversation Persistence Model
- Persistence model is **turn-summary primary** (not standalone event-log primary).
- Delta events are persisted by **incrementally updating the same turn snapshot** (no separate delta row requirement).
- Actor model uses fixed enum baseline (`user`, `assistant`, `system`, `plugin`) with extension capacity.
- Metadata baseline per persisted turn includes at least:
  - `tenant_id`, `client_id`, `session_id`, `turn_id`, `seq`, `trace_id`
  - `actor`, `event_type`, `created_at`
- Write policy is event-driven update: each streaming event can update current turn state/snapshot.

### History Query API Contract
- API shape uses session resource style `GET` endpoint for history retrieval.
- Default ordering is replay-friendly ascending order.
- Pagination baseline is **turn-based** (not event-based pagination).
- Query item returns turn `status` (`in_progress` / `completed` / `error`) and current content snapshot.
- Response payload follows `snake_case` naming.

### Gateway to Skill Delivery Semantics
- Gateway uses dual-stage semantics: receive acknowledgment and persistence-status reporting are separated.
- Skill-unavailable behavior is **non-blocking async retry**, not silent log-only drop and not fail-fast session termination.
- Delivery idempotency baseline uses `session_id + turn_id + seq`.
- History/query visibility includes `delivery_status` (e.g., `pending` / `saved` / `failed`) for client-side state awareness.

### Claude's Discretion
- Exact REST path prefix and parameter names while preserving the decided API shape.
- Exact status literal sets and error code literals under the decided semantics.
- DTO field ordering and schema split (request/response object boundaries) as long as decided fields are preserved.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `pc-agent-plugin/src/core/bridge/ProtocolBridge.ts`
  - already defines bridge topics (`skill.turn.request/delta/final/completed/error`) and protocol field envelope.
- `pc-agent-plugin/src/core/runtime/BridgeRuntime.ts`
  - already enforces in-flight turn gating and `seq` anomaly detection for inbound stream handling.
- `pc-agent-plugin/src/core/events/PluginEvents.ts`
  - already provides runtime and gateway event emission shape that can drive persistence-status propagation.

### Established Patterns
- `snake_case` protocol payload convention already in place in auth and bridge layers.
- Auth and tracing baseline (`trace_id`, `session_id`) already stabilized in Phase 1/2 contracts.
- Current Java backend module in repo is gateway-auth-centric; skill persistence module is not yet present in codebase.

### Integration Points
- Gateway integration point: new forwarding path from bridge stream events into Skill persistence boundary while retaining auth boundary behavior.
- Skill service integration point: storage + query APIs must accept stream-aligned identifiers (`session_id`, `turn_id`, `seq`, `trace_id`).
- Client integration point: query response must be directly consumable by future Skill client/session view in Phase 4.

</code_context>

<specifics>
## Specific Ideas

- History APIs should favor replay correctness over convenience defaults.
- Persistence shape should keep turn-level read efficiency while still tracking stream progression.
- Delivery state must be visible enough for user-facing session continuity and troubleshooting.

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within Phase 3 scope.

</deferred>

---

*Phase: 03-skill-service-persistence-apis*
*Context gathered: 2026-03-04*
