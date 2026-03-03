# Phase 1: Gateway Auth Foundation - Research

**Researched:** 2026-03-03
**Domain:** AK/SK authentication boundary for AI-Gateway long-lived session entry
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Auth Error Contract
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

#### AK/SK Ownership Model
- Credential ownership is `tenant + app` (enterprise tenant and client application identity).
- Binding fields for auth principal are `tenant_id + client_id`.
- V1 does not enforce fine-grained capability scopes yet, but protocol must keep extension points for future scope-based authorization.
- Audit correlation key must include `tenant + app + session + trace`.
- AK format is opaque prefixed identifier (non-semantic).
- SK is stored client-side in OS keystore encrypted storage.
- Credential lifecycle state model in v1 includes `Active`, `Disabled`, `Rotating`.
- Emergency revocation requires immediate disconnect and re-authentication.

#### Session Lifecycle Policy
- Session model uses short TTL plus renewal (not long fixed session).
- Renewal is proactive at ~70% TTL.
- Reconnect always requires re-authentication.
- Concurrent sessions under same `tenant+client` are allowed with configurable caps.

### Claude's Discretion
- Exact numerical defaults for TTL/cooldown/retry windows.
- Concrete error code naming list under the `AUTH_V1_*` namespace.
- Transport payload field naming conventions (snake_case vs camelCase) if no external contract constraint exists.

### Deferred Ideas (OUT OF SCOPE)
None - discussion stayed within Phase 1 scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| AUT-01 | User/admin can configure AK/SK credentials in client runtime for gateway authentication. | Define secure client credential storage contract, config schema, and credential lifecycle (`Active/Disabled/Rotating`) handling. |
| AUT-02 | AI-Gateway validates AK/SK before accepting long-lived connection establishment. | Define gateway authentication pipeline (credential lookup, signature validation, skew window check, replay protection, principal binding). |
| AUT-03 | Invalid or missing AK/SK returns explicit authentication failure and blocks session start. | Define deterministic auth error schema, HTTP/WS mapping, retry/cooldown semantics, and redaction-safe diagnostics. |
</phase_requirements>

## Summary

Phase 1 should produce one reusable auth foundation module inside AI-Gateway that all connection-entry paths use before a session is created. The plan should treat this as a contract phase: authentication pipeline behavior and failure schema are the key deliverables, not UI behavior.

Because no application code exists yet, planning must include bootstrap tasks for gateway auth interfaces, config model, and test scaffolding. The locked decisions already narrow architecture enough to plan implementation tasks directly (no need for broad technology exploration).

**Primary recommendation:** Implement a deterministic gateway auth pipeline (`extract -> validate -> anti-replay -> principal attach -> accept/reject`) with a shared `AUTH_V1_*` error contract used consistently across HTTP and WebSocket entry paths.

## Standard Stack

### Core

| Library/Component | Version | Purpose | Why Standard |
|-------------------|---------|---------|--------------|
| JDK | 21 (fixed) | Crypto, time, secure comparison utilities | Project baseline; includes required primitives (`Mac`, `Instant`, constant-time compare support). |
| Spring Boot | 3.4.6 (fixed) | Gateway app framework and dependency management | Project baseline; predictable integration with MVC and security filters/interceptors. |
| Spring Security | Managed by Spring Boot 3.4.6 BOM | Request authentication chain and auth context integration | Standard for request auth boundaries and principal propagation in Spring systems. |
| Redis | Existing middleware | Replay key storage, cooldown counters, retry windows | Shared, low-latency state needed for anti-replay and progressive cooldown across instances. |

### Supporting

| Library/Component | Purpose | When to Use |
|-------------------|---------|-------------|
| MyBatis + MySQL 5.7 | Credential metadata/state lookup (`tenant_id`, `client_id`, AK state, rotation metadata) | Use when credentials are centrally managed by server-side store. |
| Micrometer + structured logging | Auth success/failure counters and trace correlation | Use from day 1 to support DEM-02 style cross-service tracing later. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| HMAC-signed AK/SK request validation | Plain static API key only | Simpler but cannot support replay protection and robust integrity checks. |
| Redis-backed replay/cooldown state | In-memory per-node map | Fails in multi-node deployment; weak consistency and bypass risk. |

## Architecture Patterns

### Pattern 1: Gateway Entry Auth Pipeline
**What:** A single pipeline applied before session creation across all connection entry points.
**When to use:** Every inbound attempt to create/renew a skill session.
**Pipeline order:**
1. Parse required auth fields (`ak`, signature, timestamp, nonce, tenant/client binding).
2. Resolve credential record and lifecycle state (`Active/Disabled/Rotating`).
3. Validate signature using canonical string and server-held secret.
4. Validate timestamp against skew tolerance window.
5. Validate nonce replay via Redis (`SETNX` + TTL or equivalent atomic pattern).
6. Apply failure cooldown policy and compute `retry_after` when needed.
7. Create authenticated principal with `tenant_id`, `client_id`, `trace_id`, `session_id`.
8. Pass control to session establishment only on success.

### Pattern 2: Unified Auth Error Translator
**What:** Internal auth failure reasons map to one external `AUTH_V1_*` envelope.
**When to use:** Any auth rejection for HTTP or WS.
**Rules:**
- Keep public `message` safe and non-sensitive.
- Include `next_action` and optional `retry_after`.
- Always include `trace_id`, `session_id`, `debug_id`.
- Map transport behavior separately (HTTP status vs WS close code) but keep `error_code` consistent.

### Pattern 3: Session Lifecycle Guardrails
**What:** Session TTL and renewal are auth-bound operations, not business-layer features.
**When to use:** Session creation, renewal at approximately 70% TTL, reconnect.
**Rules:**
- Reconnect always runs full auth pipeline again.
- Renewal requires still-valid credential state.
- Emergency revocation triggers forced disconnect and re-auth requirement.

### Anti-Patterns to Avoid
- Deferring auth checks until after session allocation.
- Separate error schemas per transport or per caller.
- Logging raw AK fragments, signatures, or secret-derived material.
- Relying on local process memory for replay/cooldown state.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Signature cryptography | Custom HMAC implementation | JDK `javax.crypto.Mac` and standard algorithms | Custom crypto is high-risk and unnecessary. |
| Constant-time equality | Manual string comparison | `MessageDigest.isEqual(...)` on byte arrays | Prevents timing side-channel leakage. |
| Clock/time math | Custom timestamp parser/window logic by string ops | `java.time` (`Instant`, `Duration`, `Clock`) | Reduces boundary bugs and timezone mistakes. |
| Distributed replay/cooldown state | In-process maps | Redis atomic operations with TTL | Works across multiple gateway instances. |
| Field redaction scattering | Ad-hoc redaction per call site | Centralized sanitizer/log policy | Avoids accidental secret leakage regressions. |

**Key insight:** Security-critical correctness in this phase depends on proven platform primitives and shared policies, not custom utility code.

## Common Pitfalls

### Pitfall 1: Canonical String Drift Between Client and Gateway
**What goes wrong:** Valid signatures fail because the signed payload is built differently on each side.
**Why it happens:** Undefined ordering/encoding rules for fields.
**How to avoid:** Freeze a canonicalization spec early (field order, encoding, newline rules, absent-field behavior) and add contract tests.
**Warning signs:** Spike in `invalid signature` after minor client changes.

### Pitfall 2: Overly Strict Time Window
**What goes wrong:** Legitimate clients fail auth due to moderate clock skew.
**Why it happens:** Server assumes perfect NTP sync.
**How to avoid:** Use tolerant v1 skew window and explicit skew error code with recovery action.
**Warning signs:** Region- or host-specific auth failures with otherwise valid signatures.

### Pitfall 3: Replay Protection Without Namespacing
**What goes wrong:** Nonce collisions cause false replay rejects across tenants/apps.
**Why it happens:** Replay key omits tenant/client dimensions.
**How to avoid:** Namespace replay keys by `tenant_id + client_id + ak + nonce`.
**Warning signs:** Replay rejects from unrelated clients sharing nonce generators.

### Pitfall 4: WS Upgrade Path Bypasses Auth Middleware
**What goes wrong:** WebSocket sessions establish without full auth validation.
**Why it happens:** HTTP and WS entry paths use different interceptors/filters.
**How to avoid:** Force both paths through the same auth service and translator.
**Warning signs:** AUT-02 passes for HTTP tests but fails for WS path tests.

### Pitfall 5: Secret Leakage in Logs and Error Payloads
**What goes wrong:** Sensitive credential material appears in logs or client-visible errors.
**Why it happens:** Debug logging at failure points without sanitizer.
**How to avoid:** Redaction-first logging wrappers and payload schema tests.
**Warning signs:** AK-like substrings or raw signature blobs in aggregated logs.

## Code Examples

Verified implementation patterns for this project stack:

### Signature Validation (JDK primitives)
```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
byte[] expected = mac.doFinal(canonicalPayloadBytes);
byte[] provided = Base64.getDecoder().decode(signatureB64);

if (!MessageDigest.isEqual(expected, provided)) {
    throw AuthFailure.invalidSignature();
}
```

### Replay Guard (Redis key with TTL)
```java
String replayKey = "auth:replay:%s:%s:%s:%s".formatted(tenantId, clientId, ak, nonce);
Boolean firstSeen = redisTemplate.opsForValue().setIfAbsent(replayKey, "1", Duration.ofMinutes(5));
if (!Boolean.TRUE.equals(firstSeen)) {
    throw AuthFailure.replayDetected();
}
```

### Deterministic Error Envelope Mapping
```java
AuthErrorBody body = new AuthErrorBody(
    "AUTH_V1_INVALID_SIGNATURE",
    "Authentication failed.",
    "Check client clock and credential configuration.",
    retryAfterSeconds,
    traceId,
    sessionId,
    debugId
);
return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
```

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Single API key presence check | Signed request with timestamp + nonce + replay checks | Stronger resistance to credential abuse and replay attacks. |
| Per-endpoint auth logic | Centralized auth middleware/service | Consistent behavior, easier testing, lower drift risk. |
| Generic auth error strings | Versioned machine-readable error contract (`AUTH_V1_*`) | Better client recovery behavior and future compatibility. |

## Open Questions

1. **Credential source of truth for v1**
   - What we know: Tenant/app ownership and lifecycle states are fixed.
   - What is unclear: Whether initial credential registry is DB-backed, config-backed, or external service-backed.
   - Planning impact: Determines repository interfaces, migration tasks, and integration test setup.

2. **Canonical signature spec details**
   - What we know: AK/SK validation with replay and skew handling is mandatory.
   - What is unclear: Exact canonical field set/order, encoding rules, and signed URI/query/body boundaries.
   - Planning impact: Must be frozen before client and gateway tasks split.

3. **Transport contract shape**
   - What we know: Error schema exists for both HTTP and WS with shared error codes.
   - What is unclear: Final payload naming style (`snake_case` vs `camelCase`) and WS close reason code mapping table.
   - Planning impact: Affects DTOs, contract tests, and plugin integration assumptions.

4. **Policy defaults**
   - What we know: TTL renewal (~70%), progressive cooldown, and skew tolerance are required.
   - What is unclear: Exact numerical defaults (TTL length, skew window, cooldown ladder, retry caps).
   - Planning impact: Needed for deterministic tests and ops runbooks.

5. **Client secure storage implementation baseline**
   - What we know: SK must live in OS keystore encrypted storage.
   - What is unclear: Concrete mechanism for Windows/Electron demo runtime in v1.
   - Planning impact: Needed to satisfy AUT-01 with testable acceptance criteria.

## Sources

### Primary (HIGH confidence)
- `.planning/phases/01-gateway-auth-foundation/01-CONTEXT.md` (locked decisions, discretion, scope boundaries)
- `.planning/REQUIREMENTS.md` (AUT-01, AUT-02, AUT-03 requirement definitions)
- `.planning/ROADMAP.md` (phase goal and success criteria)
- `.planning/PROJECT.md` (stack and platform constraints)
- `.planning/STATE.md` (current project status and sequencing)

### Secondary (MEDIUM confidence)
- Spring ecosystem implementation conventions inferred from project baseline (JDK 21 + Spring Boot 3.4.6 + MVC + Redis).

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- User constraints and requirement mapping: HIGH (directly from project docs)
- Standard stack fit: HIGH (fixed by project constraints)
- Architecture patterns: MEDIUM (prescriptive design from constraints; implementation details still open)
- Pitfalls: MEDIUM (security engineering best practices, not yet validated against existing code)

**Research date:** 2026-03-03
**Valid until:** 2026-04-02 (or until credential source/canonical signature format is decided)
