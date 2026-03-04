---
phase: 08-ai-gateway-skill-service-opencode
plan: 02
subsystem: api
tags: [redis-stream, relay, routing, dedupe, gateway, skill-service]
requires:
  - phase: 08-ai-gateway-skill-service-opencode-01
    provides: Redis route ownership truth (`tenant_id + session_id`) with `skill_owner`, `gateway_owner`, and `route_version`
provides:
  - Gateway first-hop relay branch for non-target gateway owners
  - Relay envelope model carrying route version and owner hop metadata
  - Skill-service consume->dispatch pipeline with tuple dedupe and ack/pending semantics
affects: [bridge-persistence, multi-instance-routing, relay-recovery, phase-08-plan-03]
tech-stack:
  added: []
  patterns: [owner-first relay topology, tuple-based hop dedupe, route-aware gateway publish branching]
key-files:
  created:
    - gateway/src/main/java/com/chatcui/gateway/relay/RelayEnvelope.java
    - gateway/src/main/java/com/chatcui/gateway/relay/RelayPublisher.java
    - gateway/src/main/java/com/chatcui/gateway/relay/RedisRelayPublisher.java
    - skill-service/src/main/java/com/chatcui/skill/relay/RelayEventConsumer.java
    - skill-service/src/main/java/com/chatcui/skill/relay/RelayDispatchService.java
    - skill-service/src/test/java/com/chatcui/skill/integration/CrossInstanceRelayIntegrationTest.java
  modified:
    - gateway/src/main/java/com/chatcui/gateway/runtime/BridgePersistencePublisher.java
    - gateway/src/test/java/com/chatcui/gateway/runtime/BridgePersistencePublisherTest.java
key-decisions:
  - "Non-target gateway instances publish to first-hop relay instead of local direct-forward."
  - "First-hop publish uses Redis Stream and NX dedupe key with tuple `session_id + turn_id + seq + topic`."
  - "Skill-service only ACKs relay messages after dispatch path resolves; dispatch failures remain pending."
patterns-established:
  - "Route-aware bridge publish: resume decision -> route ownership branch -> local forward or relay."
  - "Owner-first relay topology: gateway ingress -> skill owner consume -> route-selected gateway dispatch."
requirements-completed: [P08-RELAY-01, P08-DEDUPE-01]
duration: 9min
completed: 2026-03-04
---

# Phase 08 Plan 02: Cross-Instance Relay Wiring Summary

**Cross-instance owner-first relay is now wired end-to-end with route-versioned first-hop envelopes and tuple dedupe across gateway publish and skill-service consume paths.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-03-04T13:17:57Z
- **Completed:** 2026-03-04T13:26:39Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Added gateway first-hop relay model/contracts and route-aware publish branching in `BridgePersistencePublisher`.
- Ensured non-target gateway owners relay first and do not direct-forward locally for persistence topics.
- Added skill-service relay consumer/dispatcher path with deterministic consume status (`DISPATCHED`, `DUPLICATE_DROPPED`, `PENDING_RETRY`) and ACK control.
- Added integration coverage for cross-instance flow, duplicate suppression, and pending recovery on dispatch failure.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add gateway first-hop relay path for non-target owner routing**
   - `208a095` (`test`): failing relay routing tests for bridge publisher (RED)
   - `649fe18` (`feat`): gateway first-hop relay path implementation (GREEN)
2. **Task 2: Implement skill-service owner consume-and-dispatch second-hop pipeline**
   - `70a08c0` (`test`): failing cross-instance relay integration tests (RED)
   - `f73686d` (`feat`): skill-owner consume/dispatch implementation (GREEN)

## Files Created/Modified

- `gateway/src/main/java/com/chatcui/gateway/relay/RelayEnvelope.java` - First-hop relay payload model with partition key and tuple dedupe key derivation.
- `gateway/src/main/java/com/chatcui/gateway/relay/RelayPublisher.java` - Gateway relay publish contract with accepted/duplicate receipt semantics.
- `gateway/src/main/java/com/chatcui/gateway/relay/RedisRelayPublisher.java` - Redis Stream first-hop publisher with NX+TTL dedupe guard and route metadata fields.
- `gateway/src/main/java/com/chatcui/gateway/runtime/BridgePersistencePublisher.java` - Route-aware branch: local forward for target owner, relay publish for non-target owner.
- `gateway/src/test/java/com/chatcui/gateway/runtime/BridgePersistencePublisherTest.java` - Tests for non-target relay routing, target-owner local forwarding, and tuple dedupe suppression expectation.
- `skill-service/src/main/java/com/chatcui/skill/relay/RelayDispatchService.java` - Route-owner aware dispatch logic from skill owner to selected gateway owner.
- `skill-service/src/main/java/com/chatcui/skill/relay/RelayEventConsumer.java` - Consume flow with tuple dedupe, dispatch invocation, and ACK/pending behavior.
- `skill-service/src/test/java/com/chatcui/skill/integration/CrossInstanceRelayIntegrationTest.java` - Integration proof for gatewayA ingress -> skill owner consume -> gatewayB/client dispatch.

## Decisions Made

- Route ownership (`gateway_owner`) is the gate for first-hop relay behavior in gateway runtime.
- Relay dedupe tuple is normalized as `session_id|turn_id|seq|topic` and enforced at publish/consume boundaries.
- Stream ACK occurs only after dispatch path resolution; unresolved dispatch stays pending for recovery.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- `gsd-tools.cjs` default lookup under `~/.claude` was unavailable in this workspace; execution proceeded with the provided `~/.codex/get-shit-done` toolchain path.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 08-03 can build on this relay topology to add deeper recovery/observability wiring and runtime integration.
- Route-version metadata and owner hop semantics are now available in both gateway and skill-service relay layers.

---
*Phase: 08-ai-gateway-skill-service-opencode*
*Completed: 2026-03-04*

## Self-Check: PASSED

