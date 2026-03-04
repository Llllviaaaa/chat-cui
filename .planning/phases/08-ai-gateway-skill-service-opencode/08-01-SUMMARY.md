---
phase: 08-ai-gateway-skill-service-opencode
plan: 01
subsystem: gateway-routing
tags: [redis, lettuce, routing, cas, fencing]

requires:
  - phase: 07-pc-agent-plugin-architecture
    provides: deterministic resume-anchor semantics and failure envelope conventions reused by distributed routing contracts
provides:
  - Redis-backed route ownership contract (`tenant_id + session_id`) with explicit `route_version` CAS outcomes
  - Atomic owner migration primitive with immediate `fenced_owner` persistence semantics
  - Regression tests for route-key conventions and CAS success/conflict/missing behavior
affects: [08-02, 08-03, ai-gateway-relay, skill-service-owner-routing]

tech-stack:
  added: [io.lettuce:lettuce-core:6.3.2.RELEASE]
  patterns:
    - Redis hash route table keyed by cluster-safe hash tag on `tenant_id:session_id`
    - Lua-based compare-and-set migration for owner transfer and fence updates

key-files:
  created:
    - gateway/src/main/java/com/chatcui/gateway/routing/RouteOwnershipRecord.java
    - gateway/src/main/java/com/chatcui/gateway/routing/RouteOwnershipStore.java
    - gateway/src/main/java/com/chatcui/gateway/routing/RouteCasResult.java
    - gateway/src/main/java/com/chatcui/gateway/routing/RouteKeyFactory.java
    - gateway/src/main/java/com/chatcui/gateway/routing/RedisRouteOwnershipStore.java
    - gateway/src/test/java/com/chatcui/gateway/routing/RouteKeyFactoryTest.java
    - gateway/src/test/java/com/chatcui/gateway/routing/RedisRouteOwnershipStoreTest.java
  modified:
    - gateway/pom.xml

key-decisions:
  - "Use `chatcui:route:{tenant_id:session_id}` key format so all session route mutations are Redis Cluster slot-safe."
  - "Model CAS mutation outcomes explicitly as APPLIED/VERSION_CONFLICT/MISSING with conflict payload carrying active owners and current version."
  - "Use Lua script mutation for owner transfer so route update + fence persistence are atomic."

patterns-established:
  - "Routing contract fields are encoded with snake_case Redis field constants (`tenant_id`, `session_id`, `route_version`, `skill_owner`, `gateway_owner`, `fenced_owner`)."
  - "CAS transfer immediately exposes `fenced_owner` through route load reads after a successful versioned migration."

requirements-completed: [P08-ROUTE-01, P08-FENCE-01]

duration: 7 min
completed: 2026-03-04
---

# Phase 08 Plan 01: Redis Route Ownership Contract Summary

**Gateway route ownership now persists in Redis with versioned CAS transfer and immediate owner fencing semantics for multi-instance correctness.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-04T13:05:28Z
- **Completed:** 2026-03-04T13:12:31Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- Added route ownership contract layer (`RouteOwnershipRecord`, `RouteOwnershipStore`, `RouteCasResult`) with deterministic conflict modeling.
- Standardized Redis route key generation via `RouteKeyFactory` anchored on `tenant_id + session_id` hash tags.
- Implemented `RedisRouteOwnershipStore` using a Lua CAS mutation that atomically updates owners, increments `route_version`, and persists `fenced_owner`.
- Added regression tests proving key format, CAS success path, conflict metadata, missing-route outcome, and immediate fence visibility.

## Task Commits

Each task was committed atomically with TDD RED/GREEN stages:

1. **Task 1: Define route ownership contracts and Redis key conventions**
2. `db246ab` - `test(08-01): add failing test for route key conventions`
3. `6efb96c` - `feat(08-01): define route ownership contracts`
4. **Task 2: Implement atomic CAS migration + immediate fence semantics in Redis route store**
5. `d6bdc09` - `test(08-01): add failing tests for redis route CAS`
6. `8393542` - `feat(08-01): implement redis route CAS store`

## Files Created/Modified
- `gateway/src/main/java/com/chatcui/gateway/routing/RouteOwnershipRecord.java` - Canonical route record with version and owner/fence fields.
- `gateway/src/main/java/com/chatcui/gateway/routing/RouteOwnershipStore.java` - Route truth contract (`load`, `upsert`, `casTransfer`).
- `gateway/src/main/java/com/chatcui/gateway/routing/RouteCasResult.java` - Deterministic CAS result model for applied/conflict/missing outcomes.
- `gateway/src/main/java/com/chatcui/gateway/routing/RouteKeyFactory.java` - Cluster-safe route key layout (`chatcui:route:{tenant:session}`).
- `gateway/src/main/java/com/chatcui/gateway/routing/RedisRouteOwnershipStore.java` - Lua-backed atomic CAS route migration implementation.
- `gateway/src/test/java/com/chatcui/gateway/routing/RouteKeyFactoryTest.java` - Route key layout and validation tests.
- `gateway/src/test/java/com/chatcui/gateway/routing/RedisRouteOwnershipStoreTest.java` - CAS/fence behavior regression tests.
- `gateway/pom.xml` - Added `lettuce-core` dependency for Redis route store integration.

## Decisions Made
- Chose Lua CAS mutation over multi-step app-level updates to guarantee atomic route owner transfer and fence persistence.
- Added explicit `MISSING` CAS status to keep route-table absence deterministic for upstream recovery logic.
- Kept tests offline via in-memory Redis executor stub to make CAS semantics regression-fast and deterministic in CI.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Execution tooling path in workflow references (`$HOME/.claude/...`) did not exist in this workspace; commands were redirected to `C:/Users/15721/.codex/get-shit-done/bin/gsd-tools.cjs` and execution proceeded normally.
- `requirements mark-complete` could not update because `.planning/REQUIREMENTS.md` is not present in this repository layout (milestone archive path only).

## Authentication Gates

None.

## User Setup Required

Redis 6.2+ endpoint remains required for runtime integration profiles (`CHATCUI_REDIS_URL`).

## Next Phase Readiness

- `08-02` can now consume stable route store contracts and CAS semantics without redefining ownership/fence primitives.
- No blockers from this plan for downstream relay/ack pipeline work.

---
*Phase: 08-ai-gateway-skill-service-opencode*
*Completed: 2026-03-04*

## Self-Check: PASSED
