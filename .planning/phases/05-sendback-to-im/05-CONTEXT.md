# Phase 5: Sendback to IM - Context

**Gathered:** 2026-03-04
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase closes the human-in-the-loop loop by allowing users to select OpenCode output from the Skill session and send it into the current IM conversation as a normal chat message.

Scope is limited to `SKL-04`, `SVC-04`, `IMS-01`, `IMS-02`, `IMS-03`:

- selection of sendback content from Skill output
- Skill service sendback API to IM message path
- correlation storage between sendback request and originating session
- actionable failure handling for sendback attempts

It does not include reconnect hardening/observability expansion (Phase 6) or new message types/capabilities outside text sendback.

</domain>

<decisions>
## Implementation Decisions

### Carried Forward (Locked from prior phases)
- Protocol/session metadata remains aligned with prior phases: `tenant_id`, `client_id`, `session_id`, `turn_id`, `seq`, `trace_id`, `snake_case`.
- Sendback remains single-session oriented with the existing Phase 4 active-session interaction model.
- Existing deterministic error contract style continues (typed code + message, safe user-facing details).

### Selection Granularity (SKL-04)
- Both full-block selection and partial-text selection are supported.
- One sendback action sends one selected segment (single selection per send).
- Selection source is limited to assistant output content only.
- Sent IM message body uses the selected text as-is (no mandatory prefix or quote wrapper).

### Send Flow
- After selection, send action appears in-context (near the selected content).
- Sendback uses a preview-confirm step before final submit.
- User can do light text edits before confirming send.
- Sending is allowed for completed snippets even if the session is still running.

### Failure UX (IMS-03)
- Failure feedback is shown in overlay context (inline) with a lightweight top-level prompt.
- Last failed draft is retained and supports one-click retry.
- Error feedback to user includes actionable message plus deterministic error code.
- UI keeps the latest failed sendback state visible until resolved or replaced.

### Correlation and Audit (IMS-02)
- Sendback persistence must store correlation between originating session context and IM message result.
- Correlation is mandatory on backend records; UI exposure of correlation identifiers is not required in default user flow.

### Claude's Discretion
- Exact wording and placement details for sendback buttons, preview labels, and error prompts.
- Fine-grained edit constraints (character limits, trim rules) before submit.
- Whether to expose optional advanced debug details in non-default UI paths.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web-demo/src/App.tsx`
  - Existing overlay history already renders assistant snapshots per item and is the primary anchor for in-context selection/send actions.
- `web-demo/src/lib/api.ts`
  - Existing API adapter pattern can be extended with sendback endpoint calls while preserving tenant/client/session command shape.
- `skill-service/src/main/java/com/chatcui/skill/api/dto/ErrorResponse.java`
  - Existing deterministic error envelope (`error.code`, `error.message`) can be reused for sendback API failures.
- `skill-service/src/main/java/com/chatcui/skill/api/SessionHistoryExceptionHandler.java`
  - Existing handler style provides consistent mapping for deterministic request and domain errors.

### Established Patterns
- Session and trace metadata (`session_id`, `turn_id`, `trace_id`) are already persisted and queryable from phase 3.
- Web demo currently resolves interaction state by polling history and rendering compact + overlay views from shared session model.
- Backend API naming and payload style already follow `snake_case`.

### Integration Points
- New Skill service sendback API should connect to existing session context (`tenant_id`, `client_id`, `session_id`, `turn_id`, `trace_id`) and return deterministic success/failure contracts.
- Web demo overlay history item is the primary integration point for selection, preview, and sendback trigger.
- IM delivery result should flow back into Skill service persistence path for correlation and retry-friendly status visibility.

</code_context>

<specifics>
## Specific Ideas

- Keep sendback behavior aligned with normal chat behavior: user selects useful output, lightly edits if needed, and sends one message.
- Avoid forcing users to wait for entire session completion when a usable answer is already available.
- Prioritize recoverability: failures should preserve draft and provide direct retry.

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within Phase 5 scope.

</deferred>

---

*Phase: 05-sendback-to-im*
*Context gathered: 2026-03-04*

