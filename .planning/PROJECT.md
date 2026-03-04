# OpenCode Skill Bridge for Enterprise IM

## What This Is

This project delivers a Skill-based OpenCode interaction flow for enterprise IM.  
The shipped `v1.0` milestone provides gateway authentication, plugin-driven bridge runtime, persisted skill-session history, web demo interaction flow, and controlled sendback to IM.

## Core Value

Provide a reliable, secure, human-in-the-loop AI workflow inside enterprise messaging without breaking existing IM interaction patterns.

## Current State

- `v1.0 MVP` shipped on 2026-03-04 with 9 phases and 42 completed plans.
- Milestone artifacts archived under `.planning/milestones/`:
  - `v1.0-ROADMAP.md`
  - `v1.0-REQUIREMENTS.md`
  - `v1.0-MILESTONE-AUDIT.md`
- Phase 8 distributed precise-delivery closure is completed with requirement-level verification evidence (`P08-ROUTE-01..P08-OBS-01` all PASS).
- Active planning is between milestones; next step is to define `v1.1` goals and roadmap.

## Requirements Status

### Validated

- Yes Slash-triggered skill invocation loop works end-to-end (`CMD-01..03`, `SKL-01..03`, `DEM-01`).
- Yes Long-lived plugin-to-gateway conversation with enforced AK/SK auth and deterministic failure contracts (`AUT-01..03`, `BRG-01..03`).
- Yes Skill conversation persistence and query APIs are stable and replay-safe (`SVC-01..03`).
- Yes User-driven sendback to IM is supported with correlation tracking and actionable errors (`SKL-04`, `SVC-04`, `IMS-01..03`).
- Yes Reliability and observability baseline is closed with reconnect/resume/idempotency and cross-service metrics/log traceability (`BRG-04`, `DEM-02`).
- Yes Plugin architecture alignment governance is in place, including additive contract versioning and hard release gate (`P01.1-*`, `P07-*`).
- Yes Distributed multi-instance precise delivery is closed with Redis route truth, owner fence, owner-first relay, two-stage ACK, bounded recovery, and telemetry evidence (`P08-*`).

### Next Milestone Candidates

- [ ] Android/iPhone/Harmony client parity for skill trigger, session, and sendback (`MCL-01..03`).
- [ ] Role-based permission controls for skill usage (`PRD-01`).
- [ ] Per-tenant quota and throttling for invocation governance (`PRD-02`).
- [ ] Usage and audit dashboard for skill and sendback events (`PRD-03`).
- [ ] Production hardening scope for higher-scale rollout (capacity, resiliency SLO, ops automation).

### Out of Scope

- Advanced AI capability orchestration (multi-agent routing, tool marketplace) before client parity and governance hardening.
- Cross-region active-active deployment before next milestone SLO and cost baseline are defined.

## Context

- Runtime stack remains JDK 21 + Spring Boot 3.4.6 + MVC + MyBatis + MySQL 5.7 plus TypeScript plugin/web modules.
- Integration baseline now includes:
  - `pc-agent-plugin` dual-mode runtime (host plugin + CLI real-chain)
  - `gateway` auth/resume/persistence forwarding and observability contracts
  - `skill-service` persistence and IM sendback APIs
  - `web-demo` end-to-end interaction and sendback UX flow
  - distributed `gateway + skill-service` precise-delivery path (`route_version`, `OWNER_FENCED`, replay-window semantics)
- Release governance now requires `npm.cmd --prefix pc-agent-plugin run verify:phase-07` and CI alignment gate pass for plugin alignment claims.

## Constraints

- **Tech stack**: Keep existing backend baseline (JDK 21 + Spring Boot 3.4.6 + MVC + MyBatis + MySQL 5.7) - minimize platform risk.
- **Compatibility**: Must interoperate with existing IM message model and chat send APIs - avoid breaking current clients.
- **Security**: AK/SK validation is mandatory before long connection establishment - prevent unauthorized gateway access.
- **Delivery scope**: Next milestone should prioritize client expansion + governance hardening without introducing unrelated capability breadth.
- **Protocol**: OpenCode protocol must be translated into internal protocol by plugin/gateway layer - preserve backend ownership of internal contracts.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Build v1 around gateway + skill service + PC Agent + Web UI demo | Fastest path to validate full closed loop before multi-client rollout | Validated in v1.0 |
| Enforce AK/SK at connection establishment time | Security boundary must be explicit and early | Validated in v1.0 (`AUT-01..03`) |
| Keep IM sendback as server API call from Skill flow | Preserve IM source-of-truth and auditability | Validated in v1.0 (`IMS-01..03`) |
| Persist full Skill chat records in skill service | Required for continuity, replay, and troubleshooting | Validated in v1.0 (`SVC-01..03`) |
| Use release-block governance for plugin alignment claims | Prevent architecture drift and unverifiable compatibility statements | Adopted in v1.0 Phase 7 (`verify:phase-07` + CI gate) |
| Use Redis route truth + owner fence + two-stage ack for distributed precise delivery | Multi-instance topology needs deterministic ownership, ordering, and failure semantics | Validated in v1.0 Phase 8 (`P08-*`) |

## Next Milestone Goals

1. Expand from demo/client subset to multi-client parity with clear acceptance criteria.
2. Add tenant governance controls (permissions, quotas, audit visibility).
3. Preserve v1.0 compatibility and observability guarantees while scaling rollout confidence.

---
*Last updated: 2026-03-04 after v1.0 milestone re-archive with Phase 8 closure*
