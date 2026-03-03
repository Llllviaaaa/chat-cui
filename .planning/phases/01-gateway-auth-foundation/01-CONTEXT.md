# Phase 1: Gateway Auth Foundation - Context

**Gathered:** 2026-03-03
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase establishes the authentication boundary for AI-Gateway connection entry.  
Scope is limited to AK/SK-based identity validation, auth error contract, and session lifecycle baseline for secure long-lived connections.  
It does not include protocol bridge implementation, chat UI behavior, or message sendback capabilities from later phases.

</domain>

<decisions>
## Implementation Decisions

### Auth Error Contract
- Response envelope uses `error_code + message + next_action` as stable contract.
- Error codes are stage-specific (missing credentials, invalid signature, expired window, permission denied, etc.).
- User-facing message must be safe; debugging is done via `debug_id` correlation.
- Error response includes recovery guidance (`next_action`, optional `retry_after`).
- HTTP mapping is semantic: `400/401/403/429/5xx` by failure class.
- WS auth failures close connection with typed close reason and mapped `error_code`.
- Error namespace is versioned (`AUTH_V1_*`) for backward-compatible evolution.
- Minimum diagnostic fields: `trace_id`, `session_id`, `debug_id`.
- Time-skew policy: tolerate larger client/server clock skew in v1 rather than hard reject only.
- Replay detection is hard reject with dedicated replay error code.
- Repeated auth failures use progressive cooldown with explicit `retry_after`.
- Error payload enforces strict redaction (no AK fragments, no raw signature data).

### AK/SK Ownership Model
- Credential ownership is `tenant + app` (enterprise tenant and client application identity).
- Binding fields for auth principal are `tenant_id + client_id`.
- V1 does not enforce fine-grained capability scopes yet, but protocol must keep extension points for future scope-based authorization.
- Audit correlation key must include `tenant + app + session + trace`.
- AK format is opaque prefixed identifier (non-semantic).
- SK is stored client-side in OS keystore encrypted storage.
- Credential lifecycle state model in v1 includes `Active`, `Disabled`, `Rotating`.
- Emergency revocation requires immediate disconnect and re-authentication.

### Session Lifecycle Policy
- Session model uses short TTL plus renewal (not long fixed session).
- Renewal is proactive at ~70% TTL.
- Reconnect always requires re-authentication.
- Concurrent sessions under same `tenant+client` are allowed with configurable caps.

### Claude's Discretion
- Exact numerical defaults for TTL/cooldown/retry windows.
- Concrete error code naming list under the `AUTH_V1_*` namespace.
- Transport payload field naming conventions (snake_case vs camelCase) if no external contract constraint exists.

</decisions>

<specifics>
## Specific Ideas

- Product context is enterprise collaborative IM similar to DingTalk/Feishu/Spark.
- Phase 1 decisions should optimize for gateway-service integration stability before multi-client rollout.
- Windows/Electron + Web demo is current integration baseline; mobile parity is deferred.

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- No application source code exists in this repository yet; only planning artifacts are present.

### Established Patterns
- Stack and boundaries are already fixed in project context:
  - Backend baseline: JDK 21, Spring Boot 3.4.6, MVC, MyBatis, MySQL 5.7
  - Middleware: Kafka, Redis, MQ
  - Gateway-first authentication boundary before business flow

### Integration Points
- AI-Gateway: connection entry authentication interceptor/filter and error response contract.
- PC Agent plugin: credential loading and reconnect/re-auth behavior integration.
- Skill service (future phases): consume `trace_id/session_id` for cross-service correlation.

</code_context>

<deferred>
## Deferred Ideas

None - discussion stayed within Phase 1 scope.

</deferred>

---

*Phase: 01-gateway-auth-foundation*
*Context gathered: 2026-03-03*
