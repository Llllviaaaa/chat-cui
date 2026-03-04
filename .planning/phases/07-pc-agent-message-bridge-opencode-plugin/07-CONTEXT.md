# Phase 7: pc-agent-message-bridge-opencode-plugin - Context

**Gathered:** 2026-03-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Close remaining alignment gaps between current implementation and the `message-bridge-opencode-plugin` architecture baseline, then lock alignment governance for stable ongoing delivery.

This phase is an **alignment closure phase**, not a new product capability phase.
Scope is limited to `pc-agent-plugin` and its gateway-facing runtime/auth/resume contracts.
Web demo, skill-service feature expansion, and mobile parity remain out of scope.

</domain>

<decisions>
## Implementation Decisions

### Outcome and Scope Definition
- Phase 7 is positioned as **alignment closure**: no net-new user features.
- Boundary is fixed to `pc-agent-plugin + gateway` contract consistency.
- Delivery mode is **one-shot closure** (not multi-wave rollout for this phase).
- Required visible deliverable is an **alignment baseline package** including:
  - requirement mapping for this phase scope,
  - explicit "gap closed" checklist,
  - acceptance scripts/commands,
  - runbook updates.

### Contract Stability Policy
- Default policy is **strict backward compatibility** for host/plugin contracts.
- Contract evolution is **additive only** (no semantic break to existing fields/events by default).
- Key events should include an explicit contract version signal for compatibility evaluation.
- If deprecation is unavoidable, keep a **two-release window** before removal.
- Carry forward prior decision from Phase 01.1: internal versioning remains primary; no mandatory continuous upstream sync requirement.

### Acceptance Gates and Evidence
- Completion requires a **full-chain hard gate**, including no-drift, plugin host integration, CLI real-chain path, and gateway auth/resume regression coverage.
- Real-chain smoke validation is required for every release candidate.
- Gate failures block release by default; temporary waivers require owner + expiration.
- Acceptance evidence must record command, date, result, and key correlation identifiers (`session_id`, `trace_id`) for auditability.

### Claude's Discretion
- Exact field naming and placement for contract version signals.
- Exact gateway regression command composition as long as auth/resume contract coverage is preserved.
- Waiver template shape and governance metadata fields.
- Runbook document structure and evidence table formatting.

</decisions>

<specifics>
## Specific Ideas

- Keep this phase focused on closure and governance quality, not capability expansion.
- Preserve enterprise-grade traceability: every acceptance claim should map to executable command evidence.
- Prefer consistency with existing plugin-first architecture assets over introducing parallel patterns.

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `pc-agent-plugin/src/core/runtime/BridgeRuntimeFactory.ts`: shared runtime entrypoint for plugin and CLI modes.
- `pc-agent-plugin/src/host-adapter/HostPluginAdapter.ts`: host lifecycle and event bridge adapter boundary.
- `pc-agent-plugin/src/cli/runtime/CliRuntimeBootstrap.ts`: CLI bootstrap with AUTH and real-chain gating semantics.
- `pc-agent-plugin/src/core/events/PluginEvents.ts`: canonical reconnect/failure envelope types and reason code sets.
- `pc-agent-plugin/scripts/validate-no-drift.mjs` and `pc-agent-plugin/test/architecture/*`: architecture drift guardrails already in place.
- `gateway/src/main/java/com/chatcui/gateway/runtime/ResumeCoordinator.java`: gateway-side resume decision baseline for sequence/owner policy.

### Established Patterns
- Plugin-first layered ownership is already enforced: core owns bridge/auth/security; adapters remain thin.
- Event-driven host/runtime contract with deterministic failure envelopes is already the norm.
- Reliability and observability contracts from Phase 6 use low-cardinality, machine-consumable metadata.
- Quality gates are command-driven and test-suite backed (`npm`/`mvn` verification pattern).

### Integration Points
- Host inbound/outbound event contract: `pc-agent-plugin/src/host-adapter/contracts/HostPluginContract.ts`.
- Gateway topic conversion boundary: `pc-agent-plugin/src/core/bridge/ProtocolBridge.ts`.
- Runtime reconnect/resume path intersects gateway resume policy and auth expectations.
- Existing acceptance evidence conventions in `.planning/phases/01.1-*` and `.planning/phases/06-*` provide baseline format for this phase.

</code_context>

<deferred>
## Deferred Ideas

- Automated continuous upstream synchronization with the reference repository (if needed) should be treated as a separate future phase.

</deferred>

---

*Phase: 07-pc-agent-message-bridge-opencode-plugin*
*Context gathered: 2026-03-04*
