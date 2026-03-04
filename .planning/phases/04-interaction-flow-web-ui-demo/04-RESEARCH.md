# Phase 4: Interaction Flow + Web UI Demo - Research

**Researched:** 2026-03-04  
**Domain:** Slash-triggered skill interaction UX, expanded session overlay, web-demo integration with skill-service  
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- Slash trigger appears only when `/` is first character in chat input.
- Trigger panel includes `SKILL` selector with default `Local OpenCode`.
- Generate action must immediately create one-line running status card in chat.
- Expanded skill client overlays chat area fully and supports continued multi-turn conversation.
- Main chat input is hidden/disabled while expanded view is open.
- Card and overlay states remain synchronized in real time for the same session.
- If a session is already running, UI routes user to current session instead of creating parallel default session.
- Session continuity and scroll/read position should be preserved when closing/reopening overlay.

### Scope Guardrails

- In scope: `CMD-01`, `CMD-02`, `CMD-03`, `SKL-01`, `SKL-02`, `SKL-03`, `DEM-01`.
- Out of scope: sendback to IM (`SKL-04`, `SVC-04`, `IMS-*`) and reliability hardening (`BRG-04`, `DEM-02`).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CMD-01 | Slash trigger exposes SKILL selector + Local OpenCode option | Input-first-char trigger logic + selector panel contract |
| CMD-02 | User enters prompt and submits from trigger panel | Trigger-panel embedded prompt field + generate action |
| CMD-03 | Immediate acceptance feedback after generate | Accepted state + instant status-card insertion |
| SKL-01 | One-line running status card in chat | Compact card model with status badges and summary line |
| SKL-02 | Expand to overlay skill client | Full overlay layout replacing chat interaction surface |
| SKL-03 | Multi-turn continuation in skill view | Overlay composer sends additional turns in same session |
| DEM-01 | Web demo runs complete loop without native IM packaging | Standalone React+Vite demo wiring to existing backend APIs |
</phase_requirements>

## Summary

Phase 4 should split into one backend support slice and two frontend interaction slices, then close with evidence/verification. Backend needs a lightweight demo turn API that accepts prompts quickly, persists deterministic turn events, and enables polling-based UI state convergence through existing history APIs.

The frontend should center on one session view model shared by chat card and overlay. Polling history is enough for this phase to keep state consistent without introducing websocket complexity. This keeps implementation aligned with existing phase boundaries while delivering a runnable end-to-end web demo.

## Existing Codebase Leverage

- `skill-service` already has persistence and history query APIs from phase 3.
- Gateway and skill event/status semantics are already defined (`delta`, `final`, `completed`; delivery states).
- Repository has TypeScript and plugin runtime pieces but no standalone web demo shell yet.

## Recommended Execution Order

1. Add backend demo turn accept endpoint and async event simulation.
2. Scaffold `web-demo` module with slash trigger + running status card.
3. Implement overlay continuation and card/overlay sync logic.
4. Produce acceptance evidence, verification report, and phase tracking closure.

## Risks and Mitigations

- Risk: UI/backend status drift under polling.
  - Mitigation: derive status from latest history turn status and stop polling only on terminal state.
- Risk: multiple concurrent sessions create UX ambiguity.
  - Mitigation: enforce one-running-session notice path and route to current session.
- Risk: demo service scheduler leaks threads.
  - Mitigation: add service lifecycle shutdown hook for scheduler cleanup.

## Metadata

**Confidence breakdown:**
- Backend feasibility: HIGH
- Frontend interaction consistency: HIGH
- Integration and verification path: HIGH

**Research date:** 2026-03-04  
**Valid until:** 2026-04-04 (or until phase scope changes)

