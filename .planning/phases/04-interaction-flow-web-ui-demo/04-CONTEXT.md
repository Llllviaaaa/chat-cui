# Phase 4: Interaction Flow + Web UI Demo - Context

**Gathered:** 2026-03-04
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase defines end-user interaction behavior for the web demo loop:

- `/` trigger entry into SKILL invocation
- one-line running card behavior in chat
- expand-to-skill view that overlays the chat area
- consistent state between chat card and expanded skill view

Scope is limited to CMD-01/02/03, SKL-01/02/03, DEM-01. It does not include IM sendback behavior (Phase 5) or reliability hardening (Phase 6).

</domain>

<decisions>
## Implementation Decisions

### Carried Forward (Locked from prior phases)
- Protocol/session fields stay aligned to prior phases: `session_id`, `turn_id`, `seq`, `trace_id`, `snake_case`.
- Runtime contract remains stream-based (`delta`, `final`, `completed`) with persisted turn status and `delivery_status` available from backend.
- One active in-flight turn/session baseline remains the default interaction model.

### Slash Trigger and Generate Entry (CMD-01/02/03)
- Slash trigger opens SKILL selector only when `/` is entered as the first character in input.
- Selector uses compact single-column options and defaults focus/highlight to `Local OpenCode`.
- After selecting `Local OpenCode`, user enters question directly inside the same trigger panel.
- On Generate click, UI gives immediate button-processing feedback and inserts running card into chat immediately.

### Running Status Card (SKL-01)
- One-line card baseline shows: skill name + current status + expand action.
- Status model is fixed to: `waiting`, `running`, `completed`, `failed`.
- Expand action is available in both `running` and `completed` states.
- Failed state in card remains compact (failure hint only) and routes details to expanded skill view.

### Expanded Skill View (SKL-02/03)
- Expanded skill client fully overlays the chat area (not drawer/new window mode).
- Closing expanded view preserves session continuity and prior scroll/read position.
- While expanded view is open, main chat input is hidden/disabled to avoid dual-input confusion.
- Multi-turn continuation in skill view uses bottom-fixed input + explicit send action.

### Cross-View State Consistency
- Chat card and expanded skill view synchronize in real time for the same active session.
- Content generated in expanded view immediately updates card status/summary when returning to chat.
- If user triggers `/` while a skill session is already running, UI prompts and routes user back to the active session instead of creating parallel session by default.
- Re-expanding an existing session returns user to last reading position.

### Claude's Discretion
- Exact microcopy (status wording, prompt text, conflict prompt phrasing).
- Precise visual styling and animation timing for panel/card/overlay transitions.
- Exact keyboard shortcut details beyond the locked trigger behavior.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `pc-agent-plugin/src/host-adapter/contracts/HostPluginContract.ts`
  - already defines host/plugin event structure for session start/end and gateway message exchange.
- `pc-agent-plugin/src/core/runtime/BridgeRuntime.ts`
  - already tracks session lifecycle and turn progression suitable for driving UI session state.
- `skill-service/src/main/java/com/chatcui/skill/api/SessionHistoryController.java`
  - existing `GET /sessions/{session_id}/history` endpoint can hydrate expanded view history.
- `gateway/src/main/java/com/chatcui/gateway/runtime/BridgePersistencePublisher.java`
  - existing persistence-topic forwarding path aligns with running status updates.

### Established Patterns
- `snake_case` payload contract is already consistent across plugin/gateway/skill-service layers.
- Deterministic status/error contracts are already used in auth and history APIs.
- Current repository has TypeScript runtime/plugin code but no dedicated web demo UI scaffold yet.

### Integration Points
- Web demo slash/overlay UI should connect to session lifecycle from plugin host events.
- Running card status should map to backend-visible status signals (`turn_status`, `delivery_status`).
- Expanded skill view should hydrate/refresh by session history API and ongoing stream events.

</code_context>

<specifics>
## Specific Ideas

- Keep first-time path short: `/` -> select Local OpenCode -> ask -> generate -> running card appears immediately.
- Preserve one active session mental model in demo to reduce user confusion.
- Let compact card stay minimal while expanded view carries detailed conversation context.

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within Phase 4 scope.

</deferred>

---

*Phase: 04-interaction-flow-web-ui-demo*
*Context gathered: 2026-03-04*
