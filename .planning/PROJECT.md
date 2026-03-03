# OpenCode Skill Bridge for Enterprise IM

## What This Is

This project adds a Skill-based OpenCode interaction flow into an existing enterprise IM product (similar to DingTalk, Feishu, and Spark). Users can trigger a local OpenCode skill from chat, continue multi-turn AI conversation in a dedicated Skill client view, and send selected outputs back into IM as regular chat messages.  
V1 delivery focuses on backend gateway + skill service + PC Agent plugin integration, with a Web UI demo replacing native IM client for integration testing.

## Core Value

Provide a reliable, secure, human-in-the-loop AI workflow inside enterprise messaging without breaking existing IM interaction patterns.

## Requirements

### Validated

(None yet - ship to validate)

### Active

- [ ] Slash command to skill invocation flow works end-to-end.
- [ ] Long-lived OpenCode conversation through AI-Gateway is stable and authenticated.
- [ ] Skill conversation history is persisted and queryable.
- [ ] User can select AI output and send it back into IM chat through Skill service APIs.
- [ ] Web UI demo can reproduce the full interaction for cross-team integration.

### Out of Scope

- Native mobile client parity in v1 - defer until PC/Web loop is stable.
- Advanced AI capability orchestration (multi-agent routing, tool marketplace) - not required for first business validation.
- Full production hardening for global scale - this milestone is for functional and integration validation.

## Context

- Existing backend stack: JDK 21, Spring Boot 3.4.6, Spring MVC, MyBatis, MySQL 5.7.
- Existing middleware: Kafka, Redis, MQ.
- Existing clients: Android, iPhone, Harmony, Windows (Electron + React + JS/TS).
- Planned bridge component: `message-bridge-opencode-plugin` style PC Agent plugin that keeps long connection with AI-Gateway and converts protocol between OpenCode and internal IM/Skill protocol.
- Security entry condition: AK/SK must be configured client-side and validated by AI-Gateway before a long-lived session is accepted.
- Skill service responsibilities: persist records and provide APIs for history query + IM sendback.

## Constraints

- **Tech stack**: Keep existing backend baseline (JDK 21 + Spring Boot 3.4.6 + MVC + MyBatis + MySQL 5.7) - minimize platform risk.
- **Compatibility**: Must interoperate with existing IM message model and chat send APIs - avoid breaking current clients.
- **Security**: AK/SK validation is mandatory before long connection establishment - prevent unauthorized gateway access.
- **Delivery scope**: V1 targets gateway, skill service, PC Agent plugin, and Web UI demo - control milestone size.
- **Protocol**: OpenCode protocol must be translated into internal protocol by plugin/gateway layer - preserve backend ownership of internal contracts.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Build v1 around gateway + skill service + PC Agent + Web UI demo | Fastest path to validate full closed loop before multi-client rollout | Pending |
| Enforce AK/SK at connection establishment time | Security boundary must be explicit and early | Pending |
| Keep IM sendback as server API call from Skill flow | Preserve IM source-of-truth and auditability | Pending |
| Persist full Skill chat records in skill service | Required for continuity, replay, and troubleshooting | Pending |

---
*Last updated: 2026-03-03 after initialization*
