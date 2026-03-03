# Requirements: OpenCode Skill Bridge for Enterprise IM

**Defined:** 2026-03-03  
**Core Value:** Provide a reliable, secure, human-in-the-loop AI workflow inside enterprise messaging without breaking existing IM interaction patterns.

## v1 Requirements

### Command Invocation

- [ ] **CMD-01**: User can type `/` in chat input and see a `SKILL` selector including `Local OpenCode`.
- [ ] **CMD-02**: User can choose `Local OpenCode`, enter a question, and submit generation from the trigger panel.
- [ ] **CMD-03**: User receives immediate request acceptance feedback after clicking generate.

### Skill Session UX

- [ ] **SKL-01**: User sees a one-line running status card in chat after generation starts.
- [ ] **SKL-02**: User can click expand on the status card to open Skill client view over the chat area.
- [ ] **SKL-03**: User can continue multi-turn conversation with OpenCode in the Skill client.
- [ ] **SKL-04**: User can select one returned text block as sendback candidate to IM.

### Bridge and Connectivity

- [ ] **BRG-01**: Client runtime can load and initialize a PC Agent plugin compatible with `message-bridge-opencode-plugin` behavior.
- [ ] **BRG-02**: Plugin can establish and keep a long-lived connection with AI-Gateway for one skill session.
- [ ] **BRG-03**: Plugin translates messages bidirectionally between OpenCode protocol and internal protocol.
- [ ] **BRG-04**: Plugin and gateway can recover from transient disconnects and resume session continuity.

### Authentication and Security

- [ ] **AUT-01**: User/admin can configure AK/SK credentials in client runtime for gateway authentication.
- [ ] **AUT-02**: AI-Gateway validates AK/SK before accepting long-lived connection establishment.
- [ ] **AUT-03**: Invalid or missing AK/SK returns explicit authentication failure and blocks session start.

### Skill Service and Persistence

- [ ] **SVC-01**: AI-Gateway transparently forwards OpenCode streaming/output events to Skill service.
- [ ] **SVC-02**: Skill service persists full conversation history with session and actor metadata.
- [ ] **SVC-03**: Skill client can query persisted session history from Skill service APIs.
- [ ] **SVC-04**: Skill service exposes API to send selected text to IM as a chat message request.

### IM Sendback

- [ ] **IMS-01**: Selected Skill text can be sent to current IM conversation as user-visible chat message.
- [ ] **IMS-02**: Sendback request stores correlation between IM message and originating skill session.
- [ ] **IMS-03**: Sendback failures are returned with actionable error codes/messages to Skill client.

### Demo and Operations

- [ ] **DEM-01**: Web UI demo can run the complete flow without depending on native IM client packaging.
- [ ] **DEM-02**: Logs and metrics can trace one request across plugin, AI-Gateway, and Skill service.

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
| CMD-01 | Phase 4 | Pending |
| CMD-02 | Phase 4 | Pending |
| CMD-03 | Phase 4 | Pending |
| SKL-01 | Phase 4 | Pending |
| SKL-02 | Phase 4 | Pending |
| SKL-03 | Phase 4 | Pending |
| SKL-04 | Phase 5 | Pending |
| BRG-01 | Phase 2 | Pending |
| BRG-02 | Phase 2 | Pending |
| BRG-03 | Phase 2 | Pending |
| BRG-04 | Phase 6 | Pending |
| AUT-01 | Phase 1 | Pending |
| AUT-02 | Phase 1 | Pending |
| AUT-03 | Phase 1 | Pending |
| SVC-01 | Phase 3 | Pending |
| SVC-02 | Phase 3 | Pending |
| SVC-03 | Phase 3 | Pending |
| SVC-04 | Phase 5 | Pending |
| IMS-01 | Phase 5 | Pending |
| IMS-02 | Phase 5 | Pending |
| IMS-03 | Phase 5 | Pending |
| DEM-01 | Phase 4 | Pending |
| DEM-02 | Phase 6 | Pending |

**Coverage:**
- v1 requirements: 23 total
- Mapped to phases: 23
- Unmapped: 0

---
*Requirements defined: 2026-03-03*  
*Last updated: 2026-03-03 after initial definition*
