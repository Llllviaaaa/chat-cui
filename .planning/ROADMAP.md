# Roadmap: OpenCode Skill Bridge for Enterprise IM

**Created:** 2026-03-03  
**Depth:** standard  
**Mode:** yolo

## Summary

- Phases: 7
- Inserted urgent phases: 1 (`01.1`)
- v1 requirements: 23
- Mapped requirements: 23
- Unmapped requirements: 0
- Completed phases: 8 (Phase 1 verified on 2026-03-03, Phase 01.1 verified on 2026-03-04, Phase 2 verified on 2026-03-04, Phase 3 verified on 2026-03-04, Phase 4 verified on 2026-03-04, Phase 5 verified on 2026-03-04, Phase 6 verified on 2026-03-04, Phase 7 verified on 2026-03-04)

## Phase Overview

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|------------------|
| 1 | Gateway Auth Foundation | Establish secure AK/SK-gated connection entry | AUT-01, AUT-02, AUT-03 | 3 |
| 1.1 | PC Agent plugin architecture alignment (INSERTED) | Realign PC Agent to plugin-first bridge core with dual-mode runtime and no-drift gates | P01.1-ARCH-01, P01.1-ARCH-02, P01.1-ARCH-03, P01.1-CHAIN-01, P01.1-COMPAT-01, P01.1-SEC-01, P01.1-MIG-01, P01.1-DRIFT-01 | 4 |
| 2 | PC Agent Bridge Core | Deliver protocol bridge and long-connection runtime | BRG-01, BRG-02, BRG-03 | 4 |
| 3 | Skill Service Persistence APIs | Persist sessions and expose history retrieval APIs | SVC-01, SVC-02, SVC-03 | 4 |
| 4 | Interaction Flow + Web UI Demo | Ship end-user trigger and in-session interaction in demo UI | CMD-01, CMD-02, CMD-03, SKL-01, SKL-02, SKL-03, DEM-01 | 5 |
| 5 | Sendback to IM | Let user select output and send as IM message safely | SKL-04, SVC-04, IMS-01, IMS-02, IMS-03 | 5 |
| 6 | Reliability + Observability Hardening | Add reconnect and cross-service tracing for stable integration | BRG-04, DEM-02 | 4 |
| 7 | PC Agent Plugin Architecture Alignment | Close plugin+gateway alignment against `message-bridge-opencode-plugin` with auditable governance gates | P07-BASE-01, P07-REF-01, P07-COMPAT-01, P07-VERSION-01, P07-GATE-01, P07-EVID-01, P07-GOV-01 | 5 |

## Phase Details

## Phase 1: Gateway Auth Foundation

**Status:** Complete (verified 2026-03-03)

**Goal:** Ensure only authorized clients can establish Skill sessions through AI-Gateway.

**Requirements:** AUT-01, AUT-02, AUT-03

**Success Criteria:**
1. Client runtime can persist and load AK/SK config with secure handling conventions.
2. AI-Gateway rejects connection establishment when AK/SK is missing or invalid.
3. Authentication failures return deterministic error schema usable by UI/plugin layers.

### Phase 01.1: PC Agent plugin architecture alignment with message-bridge-opencode-plugin (INSERTED)

**Status:** Complete (verified 2026-03-04)

**Goal:** Enforce plugin-first PC Agent architecture with one shared bridge core for host plugin mode and standalone CLI real-chain mode, while preserving AUTH_V1 and credential security semantics.
**Requirements**: P01.1-ARCH-01, P01.1-ARCH-02, P01.1-ARCH-03, P01.1-CHAIN-01, P01.1-COMPAT-01, P01.1-SEC-01, P01.1-MIG-01, P01.1-DRIFT-01
**Depends on:** Phase 1
**Plans:** 5 plans

**Success Criteria:**
1. Plugin mode and CLI mode both run on one shared bridge core with lifecycle/event contracts enforced by tests.
2. CLI mode executes a reproducible real chain (real OpenCode + real AI-Gateway), not a mock-only path.
3. AUTH_V1 compatibility and secure credential semantics remain parity-safe against Phase 1 gateway contracts.
4. No-drift and cutover gates block completion until dual-mode evidence is produced and legacy Java runtime leaves main path.

Plans:
- [x] 01.1-01-PLAN.md - plugin-first core scaffold, architecture ADR, and stable requirement mapping
- [x] 01.1-02-PLAN.md - shared auth/security adapters with AUTH_V1 and credential parity gates
- [x] 01.1-03-PLAN.md - host plugin adapter lifecycle/event integration with no-drift checks
- [x] 01.1-04-PLAN.md - standalone CLI adapter with real OpenCode + gateway chain validation
- [x] 01.1-05-PLAN.md - CI drift enforcement, acceptance evidence, and legacy Java cutover

## Phase 2: PC Agent Bridge Core

**Status:** Complete (verified 2026-03-04)

**Goal:** Build plugin-driven protocol conversion and long-connection base flow.

**Requirements:** BRG-01, BRG-02, BRG-03

**Success Criteria:**
1. PC Agent plugin lifecycle hooks initialize and tear down cleanly with host client.
2. Long-lived gateway connection can stream request/response events for one session.
3. OpenCode protocol messages are mapped to internal protocol and back without loss of required fields.
4. Contract tests validate protocol conversion behavior for command, response, and error events.

Plans:
- [x] 02-01-PLAN.md - protocol conversion contract and stream event mapping
- [x] 02-02-PLAN.md - long-connection session runtime and in-flight control
- [x] 02-03-PLAN.md - host/CLI adapter compatibility with phase-2 stream contracts
- [x] 02-04-PLAN.md - acceptance evidence, verification, and phase tracking closure

## Phase 3: Skill Service Persistence APIs

**Status:** Complete (verified 2026-03-04)

**Goal:** Make Skill service the source for conversation storage and retrieval.

**Requirements:** SVC-01, SVC-02, SVC-03
**Plans:** 4/4 plans complete

**Success Criteria:**
1. Gateway can forward OpenCode output events to Skill service in near real time.
2. Skill service stores full conversation turns with session/user/timestamp metadata.
3. Query API returns ordered chat history for a given session with pagination baseline.
4. Storage schema and indexes are verified on MySQL 5.7 compatibility constraints.

Plans:
- [x] 03-01-PLAN.md - skill-service module/bootstrap, DTO contracts, and MySQL 5.7 schema baseline
- [x] 03-02-PLAN.md - skill-service turn persistence and session history query APIs
- [x] 03-03-PLAN.md - gateway forwarding path with non-blocking retry and delivery-status semantics
- [x] 03-04-PLAN.md - cross-module verification evidence and phase tracking closure

## Phase 4: Interaction Flow + Web UI Demo

**Status:** Complete (verified 2026-03-04)

**Goal:** Demonstrate complete user interaction loop in web-based client experience.

**Requirements:** CMD-01, CMD-02, CMD-03, SKL-01, SKL-02, SKL-03, DEM-01
**Plans:** 4/4 plans complete

**Success Criteria:**
1. Typing `/` reveals SKILL selector with Local OpenCode option.
2. User can submit question via trigger panel and receive immediate in-chat running status card.
3. Expand action opens Skill session view overlaying chat and supports continued conversation.
4. UI state remains consistent between in-chat status card and expanded Skill client view.
5. Web UI demo runs end-to-end against gateway/skill-service interfaces without native IM client dependency.

Plans:
- [x] 04-01-PLAN.md - skill-service demo turn acceptance API and async status progression
- [x] 04-02-PLAN.md - standalone web-demo slash trigger and immediate status card flow
- [x] 04-03-PLAN.md - overlay continuation and cross-view session-state synchronization
- [x] 04-04-PLAN.md - acceptance evidence, verification report, and phase tracking closure

## Phase 5: Sendback to IM

**Status:** Complete (verified 2026-03-04)

**Goal:** Close the human-in-the-loop loop by sending selected AI output back to IM chat.

**Requirements:** SKL-04, SVC-04, IMS-01, IMS-02, IMS-03
**Plans:** 4/4 plans complete

**Success Criteria:**
1. User can select one AI response segment and stage it as sendback content.
2. Skill service sendback API invokes IM message sending path with correct conversation context.
3. Successful sendback appears in IM chat timeline as normal user-visible message.
4. Correlation id links IM message and originating skill session for audit/debug.
5. Sendback failures are surfaced to Skill UI with actionable error details.

Plans:
- [x] 05-01-PLAN.md - sendback correlation persistence migration and mapper baseline
- [x] 05-02-PLAN.md - sendback API/service orchestration with deterministic errors
- [x] 05-03-PLAN.md - web-demo selection, preview-confirm, and retry UX
- [x] 05-04-PLAN.md - acceptance evidence, verification, and tracking closure

## Phase 6: Reliability + Observability Hardening

**Status:** Complete (verified 2026-03-04)

**Goal:** Increase integration reliability and troubleshooting clarity for team rollout.

**Requirements:** BRG-04, DEM-02

**Plans:** 7/7 plans complete

**Success Criteria:**
1. Connection interruption scenarios can auto-recover or fail fast with clear retry semantics.
2. Session resume behavior preserves message ordering and avoids duplicate sendback.
3. Logs include trace identifiers across plugin, gateway, and skill service boundaries.
4. Core flow dashboard/metrics can diagnose at least auth failure, bridge failure, and sendback failure classes.

Plans:
  - [x] 06-01-PLAN.md - plugin reconnect coordinator with bounded retry, fresh auth, and resume anomaly handling
  - [x] 06-02-PLAN.md - gateway resume-anchor coordinator with duplicate drop and gap compensation gating
  - [x] 06-03-PLAN.md - skill-service sendback idempotency key enforcement and duplicate-safe response behavior
  - [x] 06-04-PLAN.md - cross-service structured logging contract with shared taxonomy and deterministic failure envelope
  - [x] 06-05-PLAN.md - low-cardinality gateway/skill metrics wiring and observability baseline runbook
  - [x] 06-06-PLAN.md - phase verification evidence and tracker synchronization closure
  - [x] 06-07-PLAN.md - gap closure for runtime bridge/auth metrics wiring and integration-path observability proof

## Phase 7: PC Agent Plugin Architecture Alignment

**Status:** Complete (verified 2026-03-04)

**Goal:** Close plugin+gateway architecture alignment gaps against `message-bridge-opencode-plugin` with auditable governance artifacts and release gates.
**Requirements**: P07-BASE-01, P07-REF-01, P07-COMPAT-01, P07-VERSION-01, P07-GATE-01, P07-EVID-01, P07-GOV-01
**Depends on:** Phase 6
**Plans:** 4/4 plans complete

**Scope boundary (in):** `pc-agent-plugin` + gateway contract alignment and governance closure.
**Scope boundary (out):** web-demo capability expansion, skill-service feature expansion, and mobile parity work.

**Success Criteria:**
1. Phase-07 alignment baseline package exists with requirement mapping, gap checklist, and runbook artifacts.
2. Reference baseline snapshot for `message-bridge-opencode-plugin` is reproducible and records `reference_repo`, `reference_tag_or_commit`, `snapshot_date_utc`, and `diff_scope`.
3. Compatibility policy is explicit and testable: backward compatible, additive-only changes by default, and controlled deprecation handling.
4. Hard-gate inventory covers plugin no-drift, host integration, CLI real-chain, and gateway auth/resume regression checks.
5. Evidence and waiver governance fields are standardized for audit (`evidence_id`, owner, status, outcome, expiration).

Plans:
- [x] 07-01-PLAN.md - publish provisional requirements and baseline governance package
- [x] 07-02-PLAN.md - additive contract-version signaling and compatibility regression closure
- [x] 07-03-PLAN.md - consolidated hard-gate command and CI/governance enforcement
- [x] 07-04-PLAN.md - acceptance evidence, verification report, and tracker synchronization

## Requirement Coverage

All v1 requirements in `.planning/REQUIREMENTS.md` are mapped to exactly one phase.

### Phase 8: AI-Gateway + Skill-Service Distributed OpenCode Precise Delivery

**Status:** In Progress (started 2026-03-04)

**Goal:** Guarantee OpenCode messages are delivered to the correct target client user under distributed multi-instance `gateway` + `skill-service` topology, without wrong delivery, duplicate delivery, out-of-order replay, or ghost writes.
**Requirements**: P08-ROUTE-01, P08-FENCE-01, P08-RELAY-01, P08-DEDUPE-01, P08-ACK-01, P08-RECOVERY-01, P08-OBS-01
**Depends on:** Phase 7
**Plans:** 1/4 plans complete

**Success Criteria:**
1. Redis route table becomes source of truth keyed by `tenant_id + session_id`, with CAS versioning and immediate owner fence.
2. Non-target gateway instances relay through event bus to `skill-service owner` first, then to target `gateway/client`, preserving per-session order.
3. Full-path dedupe uses `session_id + turn_id + seq + topic`, and delivery status follows two-stage ack semantics.
4. Unknown-owner recovery is bounded to a 15-minute replay window and ends with deterministic terminal envelope when exhausted.
5. Route/fence/ack/recovery outcomes are observable through shared trace + low-cardinality metrics/log taxonomy.

Plans:
- [x] 08-01-PLAN.md - Redis route truth, CAS migration, and owner fence contract foundation
- [ ] 08-02-PLAN.md - cross-instance relay pipeline (`gateway -> skill-service owner -> target gateway/client`) with full-path dedupe
- [ ] 08-03-PLAN.md - two-stage ack, unknown-owner recovery window, and fenced-owner deterministic failure semantics
- [ ] 08-04-PLAN.md - route/fence/ack observability extension, integration evidence, and phase verification closure

---
*Last updated: 2026-03-04 after executing 08-01 (Phase 8 in progress)*


