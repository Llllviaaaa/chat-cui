---
phase: 06-reliability-observability-hardening
plan: 02
subsystem: gateway-runtime-reliability
tags: [resume-anchor, sequence-anomaly, persistence-forwarding, gateway, reconnect]
requires:
  - phase: 06-01 plugin reconnect coordinator
    provides: reconnect resume_anchor envelopes and deterministic runtime failure taxonomy
provides:
  - gateway resume decision engine for continue, duplicate-drop, gap-compensate, and terminal outcomes
  - publisher gating that blocks out-of-order tuples and emits compensate/error control envelopes
  - deterministic reason_code and next_action payload fields for resume anomaly responses
affects:
  - 06-03 skill-service sendback idempotency
  - 06-04 observability taxonomy alignment
tech-stack:
  added: []
  patterns:
    - evaluate resume anchor policy before persistence forward side effects
    - emit control envelopes for gap compensation and terminal owner conflicts
key-files:
  created:
    - gateway/src/main/java/com/chatcui/gateway/runtime/ResumeAnchor.java
    - gateway/src/main/java/com/chatcui/gateway/runtime/ResumeDecision.java
    - gateway/src/main/java/com/chatcui/gateway/runtime/ResumeCoordinator.java
    - gateway/src/test/java/com/chatcui/gateway/runtime/ResumeCoordinatorTest.java
  modified:
    - gateway/src/main/java/com/chatcui/gateway/runtime/BridgePersistencePublisher.java
    - gateway/src/main/java/com/chatcui/gateway/persistence/model/SkillTurnForwardEvent.java
    - gateway/src/main/java/com/chatcui/gateway/persistence/SkillPersistenceForwarder.java
    - gateway/src/main/java/com/chatcui/gateway/persistence/DeliveryStatusReporter.java
    - gateway/src/test/java/com/chatcui/gateway/runtime/BridgePersistencePublisherTest.java
    - gateway/src/test/java/com/chatcui/gateway/integration/SkillPersistenceForwardingIntegrationTest.java
key-decisions:
  - "Bridge persistence publishing is now gated by ResumeCoordinator decisions before any downstream forward."
  - "Resume anomaly control envelopes use reason_code and next_action fields on SkillTurnForwardEvent."
  - "Forwarder/reporter tuple keys include topic to isolate compensation control events from normal stream tuples."
patterns-established:
  - "Duplicate tuples are dropped pre-forward with explicit diagnostics."
  - "Gap tuples emit compensate controls while keeping normal continuation blocked until contiguous seq resumes."
requirements-completed: [BRG-04]
duration: 5min
completed: 2026-03-04
---

# Phase 6 Plan 02 Summary

Implemented gateway-side resume anchor enforcement with deterministic decision contracts and publish-path anomaly controls for duplicate, gap, and terminal resume cases.

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-04T03:53:32Z
- **Completed:** 2026-03-04T03:58:41Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments

- Added a standalone `ResumeCoordinator` decision engine that tracks session anchors, enforces single reconnect owner, and returns deterministic continue/drop/compensate/terminal outcomes.
- Integrated resume decisions into `BridgePersistencePublisher`, ensuring duplicates are suppressed, gaps emit compensation envelopes, and owner conflicts emit terminal failure envelopes with `reason_code + next_action`.
- Expanded gateway forwarding payload and tests to validate deterministic resume anomaly behavior in both runtime-level and integration-level persistence flows.

## Task Commits

1. **Task 1: Create resume-anchor coordinator and deterministic decision contracts (RED)** - `338cd45` (test)
2. **Task 1: Create resume-anchor coordinator and deterministic decision contracts (GREEN)** - `294aaa0` (feat)
3. **Task 2: Wire resume decisions into publish/forward pipeline and integration tests (RED)** - `3268d92` (test)
4. **Task 2: Wire resume decisions into publish/forward pipeline and integration tests (GREEN)** - `15ac8ce` (feat)

## Files Created/Modified

- `gateway/src/main/java/com/chatcui/gateway/runtime/ResumeAnchor.java` - immutable resume tuple contract.
- `gateway/src/main/java/com/chatcui/gateway/runtime/ResumeDecision.java` - deterministic decision/result model with reason and next action metadata.
- `gateway/src/main/java/com/chatcui/gateway/runtime/ResumeCoordinator.java` - session anchor tracking and owner-conflict/gap/duplicate decision logic.
- `gateway/src/main/java/com/chatcui/gateway/runtime/BridgePersistencePublisher.java` - resume-aware forward gating, compensation emission, and terminal envelope emission.
- `gateway/src/main/java/com/chatcui/gateway/persistence/model/SkillTurnForwardEvent.java` - added `reason_code` and `next_action` fields with backward-compatible constructor.
- `gateway/src/main/java/com/chatcui/gateway/persistence/SkillPersistenceForwarder.java` - tuple dedupe key now includes topic for control-event isolation.
- `gateway/src/main/java/com/chatcui/gateway/persistence/DeliveryStatusReporter.java` - status key now includes topic for consistent tuple isolation.
- `gateway/src/test/java/com/chatcui/gateway/runtime/ResumeCoordinatorTest.java` - continue/drop/compensate/terminal decision matrix tests.
- `gateway/src/test/java/com/chatcui/gateway/runtime/BridgePersistencePublisherTest.java` - compensation and terminal envelope forwarding tests.
- `gateway/src/test/java/com/chatcui/gateway/integration/SkillPersistenceForwardingIntegrationTest.java` - integration proof that gap anomalies emit compensate events and block out-of-order tuples.

## Decisions Made

- Resume decisions are evaluated inside the gateway publish boundary, so persistence forwarding only sees allowed continuation or explicit anomaly control envelopes.
- Terminal resume conflicts are represented as `skill.turn.error` events with stable machine-readable `reason_code` and `next_action`.
- Gap compensation control events reuse the existing forward path but are isolated from normal stream tuple dedupe via topic-aware tuple keys.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Compensation control tuples could collide with normal seq tuple dedupe keys**
- **Found during:** Task 2 (publisher integration wiring)
- **Issue:** Compensation events use the same `session_id + turn_id + seq` tuple space as normal stream events, so forwarder/status dedupe could incorrectly suppress subsequent contiguous recovery tuples.
- **Fix:** Extended tuple/status keys to include `topic`, isolating anomaly control envelopes from normal stream tuples while preserving duplicate suppression per topic.
- **Files modified:** `gateway/src/main/java/com/chatcui/gateway/persistence/SkillPersistenceForwarder.java`, `gateway/src/main/java/com/chatcui/gateway/persistence/DeliveryStatusReporter.java`
- **Verification:** `.\\mvnw.cmd -pl gateway "-Dtest=ResumeCoordinatorTest,BridgePersistencePublisherTest,SkillPersistenceForwardingIntegrationTest" test`
- **Committed in:** `15ac8ce`

---

**Total deviations:** 1 auto-fixed (Rule 1 bug)
**Impact on plan:** Required correctness fix to avoid replay-path corruption; no scope creep beyond anomaly control behavior.

## Issues Encountered

- PowerShell requires quoting/composing the multi-test Maven argument; execution switched to `.\\mvnw.cmd` with quoted `-Dtest=...` and continued.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Gateway resume continuity semantics are now deterministic and test-covered for duplicate/gap/terminal paths.
- Plan `06-03` can safely build sendback idempotency on top of this gateway-side continuity guardrail.

## Self-Check: PASSED

- Found `.planning/phases/06-reliability-observability-hardening/06-02-SUMMARY.md`
- Found commit `338cd45`
- Found commit `294aaa0`
- Found commit `3268d92`
- Found commit `15ac8ce`
