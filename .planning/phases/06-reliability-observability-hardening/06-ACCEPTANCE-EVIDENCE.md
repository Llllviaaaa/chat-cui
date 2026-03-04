# Phase 6 Acceptance Evidence

## Scope

Validate `BRG-04` and `DEM-02` closure for reliability + observability hardening:

- Reconnect and resume paths recover deterministically or fail fast with actionable metadata.
- Resume sequencing protects continuity (duplicate-drop, gap compensation, owner conflict handling).
- Sendback idempotency prevents duplicate IM dispatch across retry/reconnect scenarios.
- Cross-service observability emits consistent `trace_id`, `failure_class`, and retry semantics.

## Evidence Log

| Area | Command | Date | Result | Notes |
| --- | --- | --- | --- | --- |
| Plugin reconnect + resume runtime | `npm.cmd --prefix pc-agent-plugin run test:bridge-runtime` | 2026-03-04 | PASS | `BridgeSessionRuntime.integration.test.ts` (5 passed) validates reconnect lifecycle and resume-continuity behavior. |
| Gateway resume coordinator + forwarding path | `./mvnw.cmd -pl gateway -am test` | 2026-03-04 | PASS | 43 tests passed, including `ResumeCoordinatorTest`, `BridgePersistencePublisherTest`, and forwarding integration cases for duplicate/gap/terminal resume outcomes. |
| Skill-service idempotency + persistence observability | `./mvnw.cmd -pl skill-service -am test` | 2026-03-04 | PASS | 36 tests passed, covering sendback idempotency replay (`SendbackServiceTest`) and persistence envelope behavior (`TurnPersistenceServiceTest`). |
| Demo flow regression guard | `npm.cmd --prefix web-demo run test` | 2026-03-04 | PASS | 5 tests passed; verifies user-facing flow continuity stays intact with reliability hardening changes. |

## Requirement Mapping

- `BRG-04`:
  - Plugin reconnect coordinator and resume anomaly handling:
    - `pc-agent-plugin/src/core/runtime/BridgeRuntime.ts`
    - `pc-agent-plugin/src/core/runtime/SessionGatewayTransport.ts`
    - `pc-agent-plugin/test/integration/core/BridgeSessionRuntime.integration.test.ts`
  - Gateway resume-anchor coordinator and continuity enforcement:
    - `gateway/src/main/java/com/chatcui/gateway/runtime/ResumeCoordinator.java`
    - `gateway/src/main/java/com/chatcui/gateway/runtime/BridgePersistencePublisher.java`
    - `gateway/src/test/java/com/chatcui/gateway/runtime/ResumeCoordinatorTest.java`
    - `gateway/src/test/java/com/chatcui/gateway/integration/SkillPersistenceForwardingIntegrationTest.java`
  - Skill-service duplicate-safe sendback replay:
    - `skill-service/src/main/java/com/chatcui/skill/service/SendbackService.java`
    - `skill-service/src/test/java/com/chatcui/skill/service/SendbackServiceTest.java`

- `DEM-02`:
  - Shared observability taxonomy + structured envelopes:
    - `pc-agent-plugin/src/core/events/PluginEvents.ts`
    - `gateway/src/main/java/com/chatcui/gateway/observability/FailureClass.java`
    - `skill-service/src/main/java/com/chatcui/skill/observability/FailureClass.java`
  - Persistence boundary structured failure logs and contracts:
    - `gateway/src/main/java/com/chatcui/gateway/persistence/SkillPersistenceForwarder.java`
    - `skill-service/src/main/java/com/chatcui/skill/service/TurnPersistenceService.java`
  - Baseline metrics/runbook artifacts:
    - `.planning/phases/06-reliability-observability-hardening/06-OBSERVABILITY-BASELINE.md`

## Observability Proof Snippets (From Test Runs)

- Gateway structured persistence-failure envelopes emitted with required fields including `trace_id` and `failure_class` (example keys seen in test output): `tenant_id`, `client_id`, `session_id`, `turn_id`, `seq`, `trace_id`, `error_code`, `component`, `status`, `failure_class`, `retryable`.
- Gateway resume anomaly logs emitted deterministic reconnect metadata for compensation and terminal paths (`reason_code`, `next_action`, session/turn/seq diagnostics).

