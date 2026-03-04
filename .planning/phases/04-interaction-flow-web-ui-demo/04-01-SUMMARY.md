---
phase: 04-interaction-flow-web-ui-demo
plan: 01
subsystem: skill-service-demo-api
tags: [spring-boot, skill-service, demo-turn]
requires:
  - phase: 03-skill-service-persistence-apis
    provides: Persist/query baseline for turn history and status lifecycle
provides:
  - Demo turn acceptance endpoint for generate and follow-up prompts
  - Deterministic async turn progression (delta/final/completed) for polling UI
  - Request validation and deterministic INVALID_REQUEST contract
affects: [04-02-PLAN, 04-03-PLAN, web-demo-api-contract]
tech-stack:
  added: []
  patterns: [accepted-then-async-persist, per-session-monotonic-seq]
key-files:
  created:
    - skill-service/src/main/java/com/chatcui/skill/api/SkillDemoTurnController.java
    - skill-service/src/main/java/com/chatcui/skill/api/SkillDemoTurnExceptionHandler.java
    - skill-service/src/main/java/com/chatcui/skill/api/dto/DemoTurnRequest.java
    - skill-service/src/main/java/com/chatcui/skill/api/dto/DemoTurnAcceptedResponse.java
    - skill-service/src/main/java/com/chatcui/skill/service/SkillDemoTurnService.java
    - skill-service/src/test/java/com/chatcui/skill/service/SkillDemoTurnServiceTest.java
  modified: []
key-decisions:
  - "Keep turn accept API under /demo/skill/sessions/{session_id}/turns to isolate phase-4 demo behavior."
  - "Use scheduled persistence progression to emulate running->completed lifecycle without changing gateway stream runtime."
patterns-established:
  - "Return 202 accepted immediately, then converge status via existing history polling."
  - "Shut down scheduled executor with bean lifecycle hook to avoid residual worker threads."
requirements-completed: [CMD-03, SKL-03]
completed: 2026-03-04
---

# 04-01 Summary

Added the backend demo-turn entrypoint required by Phase 4 web UI interactions. The service now accepts prompt turns immediately and persists deterministic progression events so UI polling can show running and completed states.

## Verification

- `./mvnw.cmd -pl skill-service -Dtest=SkillDemoTurnServiceTest test`
- `./mvnw.cmd -pl gateway,skill-service -am test`

## Task Commits

1. `b02de61` - add demo turn controller/dto/service and service-level tests

## Self-Check: PASSED

