# Requirements: OpenCode Skill Bridge for Enterprise IM

**Defined:** 2026-03-03  
**Core Value:** Provide a reliable, secure, human-in-the-loop AI workflow inside enterprise messaging without breaking existing IM interaction patterns.

## v1 Requirements

### Command Invocation

- [x] **CMD-01**: User can type `/` in chat input and see a `SKILL` selector including `Local OpenCode`.
- [x] **CMD-02**: User can choose `Local OpenCode`, enter a question, and submit generation from the trigger panel.
- [x] **CMD-03**: User receives immediate request acceptance feedback after clicking generate.

### Skill Session UX

- [x] **SKL-01**: User sees a one-line running status card in chat after generation starts.
- [x] **SKL-02**: User can click expand on the status card to open Skill client view over the chat area.
- [x] **SKL-03**: User can continue multi-turn conversation with OpenCode in the Skill client.
- [x] **SKL-04**: User can select one returned text block as sendback candidate to IM.

### Bridge and Connectivity

- [x] **BRG-01**: Client runtime can load and initialize a PC Agent plugin compatible with `message-bridge-opencode-plugin` behavior.
- [x] **BRG-02**: Plugin can establish and keep a long-lived connection with AI-Gateway for one skill session.
- [x] **BRG-03**: Plugin translates messages bidirectionally between OpenCode protocol and internal protocol.
- [x] **BRG-04**: Plugin and gateway can recover from transient disconnects and resume session continuity.

### Authentication and Security

- [x] **AUT-01**: User/admin can configure AK/SK credentials in client runtime for gateway authentication.
- [x] **AUT-02**: AI-Gateway validates AK/SK before accepting long-lived connection establishment.
- [x] **AUT-03**: Invalid or missing AK/SK returns explicit authentication failure and blocks session start.

### Skill Service and Persistence

- [x] **SVC-01**: AI-Gateway transparently forwards OpenCode streaming/output events to Skill service.
- [x] **SVC-02**: Skill service persists full conversation history with session and actor metadata.
- [x] **SVC-03**: Skill client can query persisted session history from Skill service APIs.
- [x] **SVC-04**: Skill service exposes API to send selected text to IM as a chat message request.

### IM Sendback

- [x] **IMS-01**: Selected Skill text can be sent to current IM conversation as user-visible chat message.
- [x] **IMS-02**: Sendback request stores correlation between IM message and originating skill session.
- [x] **IMS-03**: Sendback failures are returned with actionable error codes/messages to Skill client.

### Demo and Operations

- [x] **DEM-01**: Web UI demo can run the complete flow without depending on native IM client packaging.
- [x] **DEM-02**: Logs and metrics can trace one request across plugin, AI-Gateway, and Skill service.

### Phase 01.1 Architecture Correction (Provisional IDs)

- [x] **P01.1-ARCH-01**: Runtime architecture is plugin-first and not standalone-app-first.
- [x] **P01.1-ARCH-02**: Layered topology (`core/bridge/host-adapter`) with shared lifecycle/event contracts is established.
- [x] **P01.1-ARCH-03**: Plugin mode and CLI mode both instantiate one shared core runtime.
- [x] **P01.1-CHAIN-01**: CLI mode enforces real OpenCode + AI-Gateway chain configuration (no mock-only shortcut).
- [x] **P01.1-COMPAT-01**: AUTH_V1 signing/error compatibility remains aligned with gateway contracts.
- [x] **P01.1-SEC-01**: Credential semantics preserve typed secure-storage behavior and no secret leakage.
- [x] **P01.1-MIG-01**: Java `pc-agent` is removed from root main-path module wiring after evidence gate.
- [x] **P01.1-DRIFT-01**: No-drift automation prevents adapter/core ownership regressions.

### Phase 07 Alignment Closure (Provisional IDs)

Scope lock: these requirements only cover `pc-agent-plugin` + gateway alignment closure and governance. They do not introduce web-demo, skill-service, or mobile feature expansion.

- [ ] **P07-BASE-01**: Publish a complete phase-07 alignment baseline package with requirement mapping, gap checklist, and runbook artifacts.
  Completion signals: all three artifacts exist in `.planning/phases/07-pc-agent-message-bridge-opencode-plugin/` and include executable gate/evidence references.
- [ ] **P07-REF-01**: Record a reproducible reference snapshot for `message-bridge-opencode-plugin` used for alignment claims.
  Completion signals: snapshot record captures `reference_repo`, `reference_tag_or_commit`, `snapshot_date_utc`, and `diff_scope`, with an auditable evidence identifier.
- [ ] **P07-COMPAT-01**: Enforce backward-compatible, additive-only contract alignment between plugin host boundary and gateway-facing bridge behaviors.
  Completion signals: compatibility assertions and documented policy show no breaking removals and clear handling for additive fields/events.
- [ ] **P07-VERSION-01**: Standardize explicit contract-version signaling for key runtime/plugin boundary events involved in alignment evidence.
  Completion signals: mapping/runbook enumerate versioned events and verification commands that prove version metadata presence.
- [ ] **P07-GATE-01**: Define a release-blocking phase-07 hard-gate inventory spanning plugin no-drift, host integration, CLI real-chain, and gateway auth/resume checks.
  Completion signals: runbook and mapping provide concrete command set, expected outputs, and pass/fail gate criteria.
- [ ] **P07-EVID-01**: Standardize phase-07 acceptance evidence so each claim includes command, date, result, `session_id`, `trace_id`, and `evidence_id`.
  Completion signals: evidence schema is documented and linked from requirement mapping/checklist entries.
- [ ] **P07-GOV-01**: Define waiver/deprecation governance requirements with owner accountability and expiry control.
  Completion signals: checklist/runbook include `owner`, `status`, waiver reference, expiration policy, and closure rules for temporary exceptions.

## v2 Requirements

### Multi-Client Expansion

- **MCL-01**: Android client supports slash-trigger and skill-session interaction parity.
- **MCL-02**: iPhone client supports slash-trigger and skill-session interaction parity.
- **MCL-03**: Harmony client supports slash-trigger and skill-session interaction parity.

### Product Hardening

- **PRD-01**: Add role-based permission control for skill usage by org policy.
- **PRD-02**: Add per-tenant quota and throttling for skill invocations.
- **PRD-03**: Add audit dashboard for skill usage and sendback events.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Rich document/code artifact embedding in sendback | Not required to validate baseline interaction loop |
| Multi-agent orchestration in one session | Increases complexity beyond first milestone |
| Cross-region active-active deployment | Production scale concern, defer after functional validation |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| CMD-01 | Phase 4 | Complete |
| CMD-02 | Phase 4 | Complete |
| CMD-03 | Phase 4 | Complete |
| SKL-01 | Phase 4 | Complete |
| SKL-02 | Phase 4 | Complete |
| SKL-03 | Phase 4 | Complete |
| SKL-04 | Phase 5 | Complete |
| BRG-01 | Phase 2 | Complete |
| BRG-02 | Phase 2 | Complete |
| BRG-03 | Phase 2 | Complete |
| BRG-04 | Phase 6 | Complete |
| AUT-01 | Phase 1 | Complete |
| AUT-02 | Phase 1 | Complete |
| AUT-03 | Phase 1 | Complete |
| SVC-01 | Phase 3 | Complete |
| SVC-02 | Phase 3 | Complete |
| SVC-03 | Phase 3 | Complete |
| SVC-04 | Phase 5 | Complete |
| IMS-01 | Phase 5 | Complete |
| IMS-02 | Phase 5 | Complete |
| IMS-03 | Phase 5 | Complete |
| DEM-01 | Phase 4 | Complete |
| DEM-02 | Phase 6 | Complete |
| P01.1-ARCH-01 | Phase 01.1 | Complete |
| P01.1-ARCH-02 | Phase 01.1 | Complete |
| P01.1-ARCH-03 | Phase 01.1 | Complete |
| P01.1-CHAIN-01 | Phase 01.1 | Complete |
| P01.1-COMPAT-01 | Phase 01.1 | Complete |
| P01.1-SEC-01 | Phase 01.1 | Complete |
| P01.1-MIG-01 | Phase 01.1 | Complete |
| P01.1-DRIFT-01 | Phase 01.1 | Complete |
| P07-BASE-01 | Phase 7 | In Progress |
| P07-REF-01 | Phase 7 | In Progress |
| P07-COMPAT-01 | Phase 7 | In Progress |
| P07-VERSION-01 | Phase 7 | In Progress |
| P07-GATE-01 | Phase 7 | In Progress |
| P07-EVID-01 | Phase 7 | In Progress |
| P07-GOV-01 | Phase 7 | In Progress |

**Coverage:**
- v1 requirements: 23 total
- Mapped to phases: 23
- Unmapped: 0

---
*Requirements defined: 2026-03-03*  
*Last updated: 2026-03-04 during Phase 7 Plan 07-01 execution*
