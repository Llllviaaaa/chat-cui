# Phase 5 Acceptance Evidence

## Scope

Validate `SKL-04`, `SVC-04`, `IMS-01`, `IMS-02`, `IMS-03`:

- Skill overlay supports selecting assistant output (full or partial) as sendback candidate.
- Skill service sendback API submits selected/edited text to IM path.
- Successful sendback returns IM message id and is user-visible in send result prompt.
- Sendback record persists correlation between request, session, turn, trace, and IM result.
- Failure response is actionable and enables retained-draft retry flow.

## Evidence Log

| Area | Command | Date | Result | Notes |
| --- | --- | --- | --- | --- |
| Sendback API and service behavior | `./mvnw.cmd -pl skill-service "-Dtest=SendbackServiceTest,SendbackControllerIntegrationTest" test` | 2026-03-04 | PASS | Validates success, invalid selection, IM failure mapping, and deterministic controller errors. |
| Schema correlation baseline | `./mvnw.cmd -pl skill-service -Dtest=SkillTurnSchemaCompatibilityTest test` | 2026-03-04 | PASS | Confirms `skill_sendback_record` table and correlation indexes/keys in V2 migration. |
| Full backend regression | `./mvnw.cmd -pl gateway,skill-service -am test` | 2026-03-04 | PASS | Gateway + skill-service all tests green with phase-5 additions. |
| Web sendback UX behavior | `npm.cmd --prefix web-demo run test` | 2026-03-04 | PASS | Tests include full sendback success and partial-selection failure+retry flow. |
| Web demo build | `npm.cmd --prefix web-demo run build` | 2026-03-04 | PASS | Standalone demo build succeeds with sendback UI updates. |

## Requirement Mapping

- `SKL-04`:
  - `web-demo/src/App.tsx`
  - `web-demo/src/App.test.tsx` (`full sendback...`, `partial selection...`)

- `SVC-04`:
  - `skill-service/src/main/java/com/chatcui/skill/api/SendbackController.java`
  - `skill-service/src/main/java/com/chatcui/skill/service/SendbackService.java`
  - `skill-service/src/test/java/com/chatcui/skill/api/SendbackControllerIntegrationTest.java`

- `IMS-01`:
  - `skill-service/src/main/java/com/chatcui/skill/service/ImMessageGateway.java`
  - `skill-service/src/main/java/com/chatcui/skill/service/LocalImMessageGateway.java`
  - `web-demo/src/App.tsx` (success banner after sendback)

- `IMS-02`:
  - `skill-service/src/main/resources/db/migration/V2__skill_sendback_record.sql`
  - `skill-service/src/main/resources/mybatis/SendbackRecordMapper.xml`
  - `skill-service/src/test/java/com/chatcui/skill/service/SendbackServiceTest.java`

- `IMS-03`:
  - `skill-service/src/main/java/com/chatcui/skill/api/SendbackExceptionHandler.java`
  - `web-demo/src/lib/api.ts`
  - `web-demo/src/App.tsx` (inline+banner failure and retained retry draft)

