# Phase 2: PC Agent Bridge Core - Context

**Gathered:** 2026-03-04
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers plugin-side bridge core behavior for one long-lived skill session:

- OpenCode <-> internal protocol bidirectional conversion
- session lifecycle baseline on long connection
- stream event contract for request/response/error in one session

It does not add new UI capabilities, persistence APIs, sendback behavior, or reliability hardening beyond the baseline required for BRG-01/02/03.

</domain>

<decisions>
## Implementation Decisions

### Carried Forward (Locked from prior phases)
- Plugin-first architecture remains fixed; no standalone-app-first runtime path.
- Core/bridge owns conversion; host/CLI adapters remain thin.
- Event-driven lifecycle contract remains `init/start/stop/dispose` with shared runtime factory.
- AUTH_V1 compatibility and secure credential semantics remain unchanged.

### Stream Event Contract
- Output streaming uses `delta + final` event model.
- In-session event ordering is strictly ordered.
- Turn completion uses explicit `turn.completed` signal.
- Runtime failures are emitted as structured error events, not plain text or silent disconnect.
- Minimum common fields on stream events: `session_id`, `turn_id`, `seq`, `trace_id`.
- For `seq` duplicate/gap anomalies in phase 2, policy is tolerant-continue (do not fail-fast in this phase).
- Bridge protocol field style is `snake_case`.
- Keepalive ping/pong is internal and not exposed as business stream events in phase 2.

### Session Concurrency Model
- One session allows only one in-flight turn at a time.
- If a new turn arrives while busy, return deterministic `BUSY` structured error.
- Plugin mode and CLI mode session namespaces are isolated.
- Session lifecycle uses explicit `start` / `end` semantics instead of implicit-only lifecycle.

### Mapping Compatibility and Failure Policy
- Unknown OpenCode event types produce explicit structured `UNSUPPORTED` events (not silent drop).
- Unknown fields in known event types are preserved under `extensions`.
- Version incompatibility is rejected with explicit `VERSION_MISMATCH`.
- Mapping failure payload must include `code`, `message`, `event_type`, `trace` (no raw sensitive payload dump).

### Claude's Discretion
- Exact event name constants and error code literal names under the above contract.
- Exact BUSY/UNSUPPORTED/VERSION_MISMATCH status mapping across host/CLI surfaces.
- Test fixture data shape and edge-case matrix breadth as long as these decisions remain enforced.

</decisions>

<specifics>
## Specific Ideas

- Stream contract should be explicit and testable, not inferred by timeout.
- Session boundary must be deterministic to avoid hidden state across plugin and CLI modes.
- Mapping errors should preserve diagnostics without leaking sensitive payload content.

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `pc-agent-plugin/src/core/runtime/BridgeRuntime.ts`: shared lifecycle/event runtime, now with health/error emitters.
- `pc-agent-plugin/src/core/bridge/ProtocolBridge.ts`: conversion ownership anchor for OpenCode <-> gateway messages.
- `pc-agent-plugin/src/core/events/PluginEvents.ts`: existing runtime/gateway event schema baseline.
- `pc-agent-plugin/src/host-adapter/HostEventBridge.ts`: host-side event dispatch pattern that can consume stricter stream semantics.
- `pc-agent-plugin/src/cli/runtime/CliRuntimeBootstrap.ts`: CLI path already instantiates shared runtime via `createBridgeRuntime`.

### Established Patterns
- Adapter-thinness enforced by architecture tests (`HostAdapterNoDrift`, `DualModeParityNoDrift`, `PluginFirstArchitecture`).
- AUTH_V1 contracts already validated against gateway tests and docs; bridge-core work should not alter auth contracts.
- `snake_case` naming already used in auth payload fields and accepted in current docs/tests.

### Integration Points
- Host integration entry: `HostPluginAdapter.onHostEvent(...)` + `HostEventBridge.dispatchHostEvent(...)`.
- CLI integration entry: `runSession` command path emits stream events through shared runtime.
- Gateway compatibility anchor: `gateway` auth/handshake tests and contract docs under `docs/auth/*`.

</code_context>

<deferred>
## Deferred Ideas

None - discussion stayed within Phase 2 boundary.

</deferred>

---

*Phase: 02-pc-agent-bridge-core*
*Context gathered: 2026-03-04*
