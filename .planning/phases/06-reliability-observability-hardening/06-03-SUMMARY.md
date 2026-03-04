---
phase: 06-reliability-observability-hardening
plan: 03
subsystem: skill-service-sendback-reliability
tags: [sendback, idempotency, mysql57, mybatis, replay]
requires:
  - phase: 06-02 gateway resume-anchor coordinator
    provides: reconnect/retry continuity contracts and duplicate-control semantics for downstream sendback handling
provides:
  - MySQL 5.7-safe idempotency key migration with unique enforcement on sendback records
  - persistence mapper/model contract for deterministic idempotency-key replay lookups
  - duplicate-safe sendback orchestration that replays prior outcomes without second IM dispatch
affects:
  - 06-04 observability taxonomy alignment
  - 06-05 metrics baseline for sendback dedupe and failure classes
tech-stack:
  added: []
  patterns:
    - derive idempotency key from stable session context plus content fingerprint hash
    - short-circuit duplicate sendback requests to persisted outcome before IM gateway call
key-files:
  created:
    - skill-service/src/main/resources/db/migration/V3__sendback_idempotency_guard.sql
  modified:
    - skill-service/src/main/java/com/chatcui/skill/persistence/model/SendbackRecord.java
    - skill-service/src/main/java/com/chatcui/skill/persistence/mapper/SendbackRecordMapper.java
    - skill-service/src/main/resources/mybatis/SendbackRecordMapper.xml
    - skill-service/src/main/java/com/chatcui/skill/service/SendbackService.java
    - skill-service/src/main/resources/application.yml
    - skill-service/src/test/java/com/chatcui/skill/persistence/SkillTurnSchemaCompatibilityTest.java
    - skill-service/src/test/java/com/chatcui/skill/service/SendbackServiceTest.java
    - skill-service/src/test/java/com/chatcui/skill/api/SendbackControllerIntegrationTest.java
key-decisions:
  - "Idempotency key is server-derived from tenant/client/session/turn/conversation context and message fingerprint using SHA-256."
  - "Duplicate lookup happens before IM dispatch; prior `sent` records return deterministic response, prior `failed` records replay actionable failure."
  - "Migration backfills legacy rows with deterministic `legacy-{request_id}` keys before enforcing NOT NULL + unique constraint."
patterns-established:
  - "Sendback duplicate control is persistence-backed and independent from client-side retry randomness."
  - "Trace continuity for replayed successes is sourced from persisted record metadata, not retry request payload."
requirements-completed: [BRG-04]
duration: 42min
completed: 2026-03-04
---

# Phase 6 Plan 03 Summary

Implemented DB-backed sendback idempotency with deterministic replay semantics so reconnect/retry duplicates no longer trigger second IM sends.

## Performance

- **Duration:** 42 min
- **Started:** 2026-03-04T04:02:00Z
- **Completed:** 2026-03-04T04:44:21Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- Added `V3__sendback_idempotency_guard.sql` to introduce sendback `idempotency_key`, backfill legacy records, and enforce unique duplicate suppression at schema level.
- Extended MyBatis sendback persistence contracts (`SendbackRecord`, mapper interface/XML) to support idempotency-key inserts and deterministic replay lookup.
- Updated `SendbackService` to derive deterministic idempotency keys, replay persisted duplicate outcomes, and skip duplicate `ImMessageGateway.send` invocations.
- Added regression tests proving schema compatibility, service duplicate replay behavior (sent + failed), stable key derivation across trace changes, and API-level deterministic replay contract.

## Task Commits

1. **Task 1: Add sendback idempotency schema and mapper contracts** - `657ffb8` (feat)
2. **Task 2: Enforce idempotency in sendback orchestration and API-level behavior** - `9e53c71` (feat)

## Files Created/Modified

- `skill-service/src/main/resources/db/migration/V3__sendback_idempotency_guard.sql` - adds and enforces sendback idempotency key schema contract.
- `skill-service/src/main/java/com/chatcui/skill/persistence/model/SendbackRecord.java` - carries idempotency key in persistence model while preserving legacy constructor call sites.
- `skill-service/src/main/java/com/chatcui/skill/persistence/mapper/SendbackRecordMapper.java` - exposes `findByIdempotencyKey` replay lookup contract.
- `skill-service/src/main/resources/mybatis/SendbackRecordMapper.xml` - maps idempotency key column for insert and replay query paths.
- `skill-service/src/main/java/com/chatcui/skill/service/SendbackService.java` - implements deterministic key derivation and duplicate replay behavior.
- `skill-service/src/main/resources/application.yml` - adds configurable sendback idempotency hash algorithm setting.
- `skill-service/src/test/java/com/chatcui/skill/persistence/SkillTurnSchemaCompatibilityTest.java` - guards migration expectations for idempotency column/indexes.
- `skill-service/src/test/java/com/chatcui/skill/service/SendbackServiceTest.java` - validates duplicate replay short-circuiting and stable idempotency key generation.
- `skill-service/src/test/java/com/chatcui/skill/api/SendbackControllerIntegrationTest.java` - verifies deterministic API replay response contract on repeated requests.

## Decisions Made

- Keep idempotency key generation fully server-side with no dependence on caller-provided random identifiers.
- Replay duplicate failed outcomes as the same actionable `SendbackFailedException` code/message to preserve retry semantics.
- Retain previous constructor compatibility in `SendbackRecord` to avoid breaking existing call sites while transitioning to new idempotency-aware persistence contract.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] SendbackService constructor compatibility broke test wiring after hash algorithm configuration**
- **Found during:** Task 2 (service idempotency orchestration)
- **Issue:** Initial constructor-level hash algorithm injection removed the legacy 3-arg constructor path used by existing tests.
- **Fix:** Restored the 3-arg constructor and applied hash algorithm configuration through `@Value` setter injection.
- **Files modified:** `skill-service/src/main/java/com/chatcui/skill/service/SendbackService.java`
- **Verification:** `.\\mvnw.cmd -pl skill-service "-Dtest=SendbackServiceTest,SendbackControllerIntegrationTest" test`
- **Committed in:** `9e53c71`

---

**Total deviations:** 1 auto-fixed (Rule 3 blocking)
**Impact on plan:** No scope change; compatibility fix was required to complete planned idempotency behavior and preserve existing test harness.

## Issues Encountered

- PowerShell required explicit `.\\mvnw.cmd` invocation plus quoted `-Dtest=...` lists for comma-separated test classes.
- Role-guide tool path pointed to `~/.claude/get-shit-done`; this environment uses `~/.codex/get-shit-done`, so equivalent commands were executed with the local path.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Sendback duplicate suppression is now persistence-backed and regression-tested across schema, mapper, service, and API layers.
- Phase `06-04` can now align structured observability around deterministic replay outcomes (`sent`/`failed`) without duplicate IM side effects.

## Self-Check: PASSED

- Found `.planning/phases/06-reliability-observability-hardening/06-03-SUMMARY.md`
- Found commit `657ffb8`
- Found commit `9e53c71`
