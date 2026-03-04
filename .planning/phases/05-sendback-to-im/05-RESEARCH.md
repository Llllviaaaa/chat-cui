# Phase 5: Sendback to IM - Research

**Researched:** 2026-03-04  
**Domain:** Skill output selection, sendback API contract, IM send correlation and failure handling  
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- Selection supports full-block and partial-text modes.
- One sendback action sends one selected segment.
- Selection source is assistant output only.
- Message is sent to IM as selected/edited plain text (no forced wrapper).
- Send flow is in-context with preview-confirm and light edit before submit.
- Sending is allowed while session is still running (for completed snippets).
- Failure UX must retain last failed draft and support one-click retry.
- Error response must be actionable and deterministic (code + message).
- Correlation storage between sendback request and session/turn/trace is mandatory in backend.

### Scope Guardrails

- In scope: `SKL-04`, `SVC-04`, `IMS-01`, `IMS-02`, `IMS-03`.
- Out of scope: reconnect hardening/observability expansion (`BRG-04`, `DEM-02`), non-text sendback extensions.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SKL-04 | Select one returned text block as sendback candidate | Overlay item-level send actions + partial-selection capture |
| SVC-04 | Skill service API sends selected text to IM message path | Deterministic sendback REST contract and service orchestration |
| IMS-01 | Selected text appears as IM-visible message | IM gateway adapter abstraction with successful message id return |
| IMS-02 | Store correlation between IM message and skill session | Sendback persistence record keyed by request/session/turn/trace |
| IMS-03 | Return actionable failure codes/messages to Skill client | Typed exceptions + stable error code mapping and retry-friendly UX |
</phase_requirements>

## Summary

Phase 5 should deliver a small but strict sendback slice: UI selection/preview/confirm, backend send orchestration, correlation persistence, and deterministic failure contracts. The fastest safe path is to keep IM transport abstracted behind a gateway interface and persist sendback records in Skill service for auditable correlation.

For frontend, extend existing overlay-history rendering with assistant-only send affordances and a preview panel that preserves failed drafts for retries. For backend, add a dedicated sendback API and record model without changing phase-3 turn history behavior.

## Existing Codebase Leverage

- `web-demo/src/App.tsx` already has overlay history and active-session state model.
- `web-demo/src/lib/api.ts` already centralizes skill API calls and can be extended with sendback method.
- `skill-service` already has deterministic error envelope and handler pattern.
- `TurnRecordMapper` already verifies session/turn existence and carries assistant payload for selection validation.

## Recommended Execution Order

1. Add sendback persistence schema + backend service/API/error contracts.
2. Add sendback correlation and IM gateway abstraction with failure mapping tests.
3. Extend web-demo overlay with full/partial selection, preview-confirm, and retry UX.
4. Produce acceptance evidence, verification report, and phase closure updates.

## Risks and Mitigations

- Risk: invalid selection (not from assistant text) bypasses UI assumptions.
  - Mitigation: backend validation against latest assistant snapshot payload.
- Risk: sendback failures lose user context.
  - Mitigation: keep failed draft on client and persist failed sendback record with correlation and error code.
- Risk: IM integration dependency blocks phase progress.
  - Mitigation: use pluggable IM gateway adapter with deterministic local implementation for phase tests.

## Metadata

**Confidence breakdown:**
- Backend contract feasibility: HIGH
- UI extension feasibility: HIGH
- Requirement traceability and closure: HIGH

**Research date:** 2026-03-04  
**Valid until:** 2026-04-04 (or until phase scope changes)

