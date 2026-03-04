# Phase 3 Acceptance Evidence

## Scope

Validate `SVC-01`, `SVC-02`, `SVC-03` for phase 3:

- Gateway forwards `skill.turn.*` events to Skill persistence path with delivery status lifecycle visibility.
- Skill service persists conversation turns with required session/actor/trace metadata.
- Skill history query returns ordered replay with cursor pagination baseline.

## Evidence Log

| Area | Command | Date | Result | Notes |
| --- | --- | --- | --- | --- |
| Gateway forward + delivery status | `./mvnw.cmd -pl gateway,skill-service -am "-Dtest=SkillPersistenceForwardingIntegrationTest,SessionHistoryReplayIntegrationTest" test` | 2026-03-04 | PASS | `SkillPersistenceForwardingIntegrationTest` verifies delta/final/completed forwarding and `delivery_status` transition to `saved` via retry path. |
| Skill replay order + cursor | `./mvnw.cmd -pl gateway,skill-service -am "-Dtest=SkillPersistenceForwardingIntegrationTest,SessionHistoryReplayIntegrationTest" test` | 2026-03-04 | PASS | `SessionHistoryReplayIntegrationTest` verifies ascending replay and deterministic cursor continuation without duplicates. |
| Full module regression | `./mvnw.cmd -pl gateway,skill-service -am test` | 2026-03-04 | PASS | Gateway (33 tests) + Skill service (16 tests) all green after phase-3 changes. |
| MySQL 5.7 schema/index guard | `./mvnw.cmd -pl skill-service -Dtest=SkillTurnSchemaCompatibilityTest test` | 2026-03-04 | PASS | Migration contract confirms required keys/index patterns and MySQL 5.7-safe DDL baseline. |

## Requirement Mapping

- `SVC-01` (gateway transparently forwards stream/output events):
  - `gateway/src/test/java/com/chatcui/gateway/integration/SkillPersistenceForwardingIntegrationTest.java`
  - `gateway/src/main/java/com/chatcui/gateway/runtime/BridgePersistencePublisher.java`
  - `gateway/src/main/java/com/chatcui/gateway/persistence/SkillPersistenceForwarder.java`

- `SVC-02` (persist full conversation history with metadata):
  - `skill-service/src/main/java/com/chatcui/skill/service/TurnPersistenceService.java`
  - `skill-service/src/main/resources/mybatis/TurnRecordMapper.xml`
  - `skill-service/src/main/resources/db/migration/V1__skill_turn_tables.sql`

- `SVC-03` (query persisted session history):
  - `skill-service/src/main/java/com/chatcui/skill/service/SessionHistoryQueryService.java`
  - `skill-service/src/main/java/com/chatcui/skill/api/SessionHistoryController.java`
  - `skill-service/src/test/java/com/chatcui/skill/integration/SessionHistoryReplayIntegrationTest.java`
