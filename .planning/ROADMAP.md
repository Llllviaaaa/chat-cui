# Roadmap: OpenCode Skill Bridge for Enterprise IM

**Created:** 2026-03-03  
**Depth:** standard  
**Mode:** yolo

## Summary

- Phases: 6
- v1 requirements: 23
- Mapped requirements: 23
- Unmapped requirements: 0

## Phase Overview

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|------------------|
| 1 | Gateway Auth Foundation | Establish secure AK/SK-gated connection entry | AUT-01, AUT-02, AUT-03 | 3 |
| 2 | PC Agent Bridge Core | Deliver protocol bridge and long-connection runtime | BRG-01, BRG-02, BRG-03 | 4 |
| 3 | Skill Service Persistence APIs | Persist sessions and expose history retrieval APIs | SVC-01, SVC-02, SVC-03 | 4 |
| 4 | Interaction Flow + Web UI Demo | Ship end-user trigger and in-session interaction in demo UI | CMD-01, CMD-02, CMD-03, SKL-01, SKL-02, SKL-03, DEM-01 | 5 |
| 5 | Sendback to IM | Let user select output and send as IM message safely | SKL-04, SVC-04, IMS-01, IMS-02, IMS-03 | 5 |
| 6 | Reliability + Observability Hardening | Add reconnect and cross-service tracing for stable integration | BRG-04, DEM-02 | 4 |

## Phase Details

## Phase 1: Gateway Auth Foundation

**Goal:** Ensure only authorized clients can establish Skill sessions through AI-Gateway.

**Requirements:** AUT-01, AUT-02, AUT-03

**Success Criteria:**
1. Client runtime can persist and load AK/SK config with secure handling conventions.
2. AI-Gateway rejects connection establishment when AK/SK is missing or invalid.
3. Authentication failures return deterministic error schema usable by UI/plugin layers.

## Phase 2: PC Agent Bridge Core

**Goal:** Build plugin-driven protocol conversion and long-connection base flow.

**Requirements:** BRG-01, BRG-02, BRG-03

**Success Criteria:**
1. PC Agent plugin lifecycle hooks initialize and tear down cleanly with host client.
2. Long-lived gateway connection can stream request/response events for one session.
3. OpenCode protocol messages are mapped to internal protocol and back without loss of required fields.
4. Contract tests validate protocol conversion behavior for command, response, and error events.

## Phase 3: Skill Service Persistence APIs

**Goal:** Make Skill service the source for conversation storage and retrieval.

**Requirements:** SVC-01, SVC-02, SVC-03

**Success Criteria:**
1. Gateway can forward OpenCode output events to Skill service in near real time.
2. Skill service stores full conversation turns with session/user/timestamp metadata.
3. Query API returns ordered chat history for a given session with pagination baseline.
4. Storage schema and indexes are verified on MySQL 5.7 compatibility constraints.

## Phase 4: Interaction Flow + Web UI Demo

**Goal:** Demonstrate complete user interaction loop in web-based client experience.

**Requirements:** CMD-01, CMD-02, CMD-03, SKL-01, SKL-02, SKL-03, DEM-01

**Success Criteria:**
1. Typing `/` reveals SKILL selector with Local OpenCode option.
2. User can submit question via trigger panel and receive immediate in-chat running status card.
3. Expand action opens Skill session view overlaying chat and supports continued conversation.
4. UI state remains consistent between in-chat status card and expanded Skill client view.
5. Web UI demo runs end-to-end against gateway/skill-service interfaces without native IM client dependency.

## Phase 5: Sendback to IM

**Goal:** Close the human-in-the-loop loop by sending selected AI output back to IM chat.

**Requirements:** SKL-04, SVC-04, IMS-01, IMS-02, IMS-03

**Success Criteria:**
1. User can select one AI response segment and stage it as sendback content.
2. Skill service sendback API invokes IM message sending path with correct conversation context.
3. Successful sendback appears in IM chat timeline as normal user-visible message.
4. Correlation id links IM message and originating skill session for audit/debug.
5. Sendback failures are surfaced to Skill UI with actionable error details.

## Phase 6: Reliability + Observability Hardening

**Goal:** Increase integration reliability and troubleshooting clarity for team rollout.

**Requirements:** BRG-04, DEM-02

**Success Criteria:**
1. Connection interruption scenarios can auto-recover or fail fast with clear retry semantics.
2. Session resume behavior preserves message ordering and avoids duplicate sendback.
3. Logs include trace identifiers across plugin, gateway, and skill service boundaries.
4. Core flow dashboard/metrics can diagnose at least auth failure, bridge failure, and sendback failure classes.

## Requirement Coverage

All v1 requirements in `.planning/REQUIREMENTS.md` are mapped to exactly one phase.

---
*Last updated: 2026-03-03 after initial roadmap creation*
