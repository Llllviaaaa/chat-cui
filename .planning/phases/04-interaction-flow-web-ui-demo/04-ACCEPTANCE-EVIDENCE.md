# Phase 4 Acceptance Evidence

## Scope

Validate `CMD-01`, `CMD-02`, `CMD-03`, `SKL-01`, `SKL-02`, `SKL-03`, `DEM-01`:

- Slash-triggered `SKILL` selector with `Local OpenCode`.
- Prompt submission from trigger panel with immediate in-chat status card.
- Expand into full overlay skill view with continued multi-turn interaction.
- State consistency between compact card and expanded session view.
- Runnable standalone web demo integrated with existing backend APIs.

## Evidence Log

| Area | Command | Date | Result | Notes |
| --- | --- | --- | --- | --- |
| Backend regression safety | `./mvnw.cmd -pl gateway,skill-service -am test` | 2026-03-04 | PASS | Gateway (33) + skill-service (17) tests green after demo-turn API addition. |
| Demo turn persistence behavior | `./mvnw.cmd -pl skill-service -Dtest=SkillDemoTurnServiceTest test` | 2026-03-04 | PASS | Accepted response is immediate and turn stream reaches completed + delivered state. |
| Slash/card/overlay behavior tests | `npm.cmd --prefix web-demo run test` | 2026-03-04 | PASS | 3 tests validate slash selector, immediate card flow, expand+follow-up sync. |
| Web demo production build | `npm.cmd --prefix web-demo run build` | 2026-03-04 | PASS | Standalone Vite build succeeds, confirming DEM-01 runnable packaging baseline. |

## Requirement Mapping

- `CMD-01` (type `/` to reveal SKILL selector):
  - `web-demo/src/App.tsx`
  - `web-demo/src/App.test.tsx` (`typing slash at first position opens SKILL selector`)

- `CMD-02` (choose Local OpenCode, input question, submit):
  - `web-demo/src/App.tsx` (slash panel question field + generate action)
  - `web-demo/src/lib/api.ts` (turn start API adapter)

- `CMD-03` (immediate request acceptance feedback):
  - `skill-service/src/main/java/com/chatcui/skill/api/SkillDemoTurnController.java`
  - `skill-service/src/main/java/com/chatcui/skill/service/SkillDemoTurnService.java`
  - `web-demo/src/App.tsx` (waiting/running card bootstrap)

- `SKL-01` (one-line running status card):
  - `web-demo/src/App.tsx` (status card model)
  - `web-demo/src/styles.css` (compact status card rendering)

- `SKL-02` (expand to overlay skill client):
  - `web-demo/src/App.tsx` (Expand action + overlay container)
  - `web-demo/src/App.test.tsx` (`expand view supports follow-up...`)

- `SKL-03` (continue multi-turn conversation in skill client):
  - `web-demo/src/App.tsx` (overlay composer + same-session follow-up)
  - `skill-service/src/main/java/com/chatcui/skill/service/SkillDemoTurnService.java`
  - `skill-service/src/test/java/com/chatcui/skill/service/SkillDemoTurnServiceTest.java`

- `DEM-01` (complete web flow without native client dependency):
  - `web-demo/package.json`
  - `web-demo/vite.config.ts`
  - `npm.cmd --prefix web-demo run test`
  - `npm.cmd --prefix web-demo run build`

