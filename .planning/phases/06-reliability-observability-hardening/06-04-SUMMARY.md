---
phase: 06-reliability-observability-hardening
plan: 04
subsystem: observability
tags: [traceability, structured-logging, failure-taxonomy, plugin, gateway, skill-service]
requires:
  - phase: 06-01 plugin reconnect coordinator
    provides: plugin runtime failure semantics and reconnect-state event contracts
  - phase: 06-02 gateway resume-anchor coordinator
    provides: gateway persistence boundary and deterministic resume/failure reason metadata
  - phase: 06-03 sendback idempotency hardening
    provides: persistence-safe replay/failure paths that must align to shared observability taxonomy
provides:
  - Canonical failure taxonomy constants (`auth|bridge|persistence|sendback|unknown`) in plugin and both Java services
  - Required structured failure envelope contract (`tenant_id/client_id/session_id/turn_id/seq/trace_id/error_code/component/status/failure_class/retryable`)
  - Runtime structured failure logs wired in plugin CLI bootstrap failures and gateway/skill-service persistence boundaries without payload leakage
affects:
  - 06-05 metrics baseline and runbook mapping
  - 06-06 verification evidence and tracker closure
tech-stack:
  added: []
  patterns:
    - canonical failure class and retryability defaults are encoded as typed constants/enums to prevent cross-service drift
    - persistence-boundary failures emit safe, required-field envelopes with no raw payload/body content
key-files:
  created:
    - gateway/src/main/java/com/chatcui/gateway/observability/FailureClass.java
    - skill-service/src/main/java/com/chatcui/skill/observability/FailureClass.java
  modified:
    - pc-agent-plugin/src/core/events/PluginEvents.ts
    - pc-agent-plugin/src/cli/commands/runSession.ts
    - pc-agent-plugin/test/integration/cli/CliRealChain.integration.test.ts
    - gateway/src/main/java/com/chatcui/gateway/persistence/SkillPersistenceForwarder.java
    - gateway/src/test/java/com/chatcui/gateway/persistence/SkillPersistenceForwarderTest.java
    - skill-service/src/main/java/com/chatcui/skill/service/TurnPersistenceService.java
    - skill-service/src/test/java/com/chatcui/skill/service/TurnPersistenceServiceTest.java
key-decisions:
  - "Plugin CLI now logs only the required failure-envelope fields for observability correlation, while preserving existing auth error envelopes for return payloads."
  - "Gateway and skill-service persistence failures are routed through injectable log sinks (defaulting to system logger) so runtime logging remains deterministic and testable."
  - "Retryability defaults are defined alongside taxonomy constants in each runtime to keep `failure_class + retryable` semantics drift-resistant."
patterns-established:
  - "Structured failure logs at persistence boundaries contain actionable metadata only and explicitly exclude content payloads."
  - "TDD red/green commits enforce contract-first observability changes across TypeScript and Java modules."
requirements-completed: [DEM-02]
duration: 10min
completed: 2026-03-04
---

# Phase 6 Plan 04 Summary

Implemented a cross-service structured failure contract with canonical taxonomy and required trace envelope fields across plugin, gateway, and skill-service persistence paths.

## Performance

- **Duration:** 10 min
- **Started:** 2026-03-04T04:48:18Z
- **Completed:** 2026-03-04T04:58:40Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- Added canonical taxonomy contracts in plugin (`PluginEvents.ts`) and new gateway/skill-service `FailureClass` enums with explicit retryability defaults.
- Wired plugin CLI bootstrap failure logging to emit the required structured envelope fields including tenant/session/trace correlation and canonical `failure_class`.
- Added structured failure envelope emission in gateway `SkillPersistenceForwarder` and skill-service `TurnPersistenceService` persistence failure boundaries with no raw payload leakage.
- Expanded regression coverage in the required plan test suites to enforce taxonomy values, envelope field completeness, and safe logging constraints.

## Task Commits

Each task was committed atomically (TDD red/green):

1. **Task 1 RED: taxonomy/envelope contract tests** - `dda8a3f` (test)
2. **Task 1 GREEN: shared taxonomy constants/enums** - `4a50dda` (feat)
3. **Task 2 RED: structured logging contract tests** - `8b3bd24` (test)
4. **Task 2 GREEN: runtime structured logging wiring** - `a1e718c` (feat)

## Files Created/Modified

- `pc-agent-plugin/src/core/events/PluginEvents.ts` - defines canonical failure classes, retryability defaults, and required envelope field contract.
- `pc-agent-plugin/src/cli/commands/runSession.ts` - logs plugin CLI failures as required structured envelope fields.
- `pc-agent-plugin/test/integration/cli/CliRealChain.integration.test.ts` - enforces taxonomy/export contract and structured failure envelope assertions.
- `gateway/src/main/java/com/chatcui/gateway/observability/FailureClass.java` - canonical gateway taxonomy enum.
- `gateway/src/main/java/com/chatcui/gateway/persistence/SkillPersistenceForwarder.java` - emits safe structured persistence-failure envelope and supports injectable sink.
- `gateway/src/test/java/com/chatcui/gateway/persistence/SkillPersistenceForwarderTest.java` - validates taxonomy and failure-envelope contents without payload leakage.
- `skill-service/src/main/java/com/chatcui/skill/observability/FailureClass.java` - canonical skill-service taxonomy enum.
- `skill-service/src/main/java/com/chatcui/skill/service/TurnPersistenceService.java` - emits safe structured persistence-failure envelope on write errors.
- `skill-service/src/test/java/com/chatcui/skill/service/TurnPersistenceServiceTest.java` - validates taxonomy and structured failure envelope behavior.

## Decisions Made

- Keep plugin return-path auth envelopes unchanged for callers while introducing a separate observability log envelope with required DEM-02 fields.
- Use the same taxonomy literals and retryability defaults in TypeScript and Java to reduce drift risk before metrics/runbook phases.
- Treat persistence-boundary failure logs as structured metadata records only; no payload/body text is emitted.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Java `Map.of(...)` pair-count limit blocked 11-field envelope construction**
- **Found during:** Task 2 verification
- **Issue:** `Map.of(...)` supports up to 10 key/value pairs, but the required observability envelope has 11 fields.
- **Fix:** Switched envelope builders in gateway and skill-service to `Map.ofEntries(...)`.
- **Files modified:** `gateway/src/main/java/com/chatcui/gateway/persistence/SkillPersistenceForwarder.java`, `skill-service/src/main/java/com/chatcui/skill/service/TurnPersistenceService.java`
- **Verification:** `.\\mvnw.cmd -pl "gateway,skill-service" "-Dtest=SkillPersistenceForwarderTest,TurnPersistenceServiceTest" test`
- **Committed in:** `a1e718c`

---

**Total deviations:** 1 auto-fixed (Rule 3 blocking)
**Impact on plan:** No scope change; fix was required for Java correctness to satisfy the planned envelope contract.

## Issues Encountered

- Role guide command examples referenced `~/.claude/get-shit-done`; this environment uses `C:/Users/15721/.codex/get-shit-done`, so equivalent commands were executed with the local path.
- PowerShell required quoted comma-separated module/test selectors for Maven (`-pl "gateway,skill-service"` and quoted `-Dtest=...`).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- DEM-02 structured failure taxonomy and envelope baseline is now deterministic across plugin/gateway/skill-service boundaries.
- Phase `06-05` can build metrics/runbook mapping on top of stable `failure_class + retryable` semantics and required correlation fields.

## Self-Check: PASSED

- Found `.planning/phases/06-reliability-observability-hardening/06-04-SUMMARY.md`
- Found commit `dda8a3f`
- Found commit `4a50dda`
- Found commit `8b3bd24`
- Found commit `a1e718c`
