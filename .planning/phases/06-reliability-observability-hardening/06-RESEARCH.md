# Phase 6: Reliability + Observability Hardening - Research

**Researched:** 2026-03-04  
**Domain:** Reconnect/resume reliability, duplicate-safe sendback, and cross-service observability baseline  
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

### Connection Recovery Policy
- Default policy is automatic reconnect with bounded attempts, not immediate fail-fast.
- Retry pacing uses exponential backoff with jitter.
- Every reconnect attempt must perform fresh AK/SK authentication before resume.
- Runtime surfaces phased recovery states to upper layers: `reconnecting`, `resumed`, `failed`.
- Terminal failure payload must include deterministic reason code and `next_action`.

### Session Continuity and Duplicate Control
- Resume anchor is last acknowledged tuple: `session_id + turn_id + seq`.
- On sequence anomalies after reconnect:
  - duplicate `seq` events are dropped with diagnostic records
  - gap events trigger compensation/replay before normal continuation
- Same session uses a single-owner reconnect flow (no concurrent multi-owner resume).
- Sendback must use server-side idempotency keys (session context + content fingerprint) to block duplicate IM sends during reconnect/retry windows.

### Observability Baseline and Failure Taxonomy
- One `trace_id` is propagated end-to-end across plugin, gateway, and skill-service boundaries.
- Structured logs use a shared minimum field set:
  `tenant_id`, `client_id`, `session_id`, `turn_id`, `seq`, `trace_id`, `error_code`, `component`, `status`.
- Cross-service failure taxonomy is unified as:
  `auth`, `bridge`, `persistence`, `sendback`, `unknown`.
- Every failure class carries retry semantics (`retryable` vs `terminal`).
- Delivery mode is dual-track: structured logs for trace drill-down + metrics for dashboard/alerting.

### Claude's Discretion
- Exact reconnect max-attempt and backoff cap values.
- Jitter formula and reconnect cooldown tuning.
- Idempotency key hashing algorithm and idempotency window TTL.
- Exact metric names, labels, and dashboard layout details.

### Deferred Ideas (OUT OF SCOPE)
None - discussion stayed within Phase 6 scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| BRG-04 | Plugin and gateway can recover from transient disconnects and resume session continuity. | Reconnect coordinator with bounded retry + jitter + fresh auth, resume-anchor protocol, sequence anomaly handling (drop duplicate / compensate gap), single-owner reconnect lock. |
| DEM-02 | Logs and metrics can trace one request across plugin, AI-Gateway, and Skill service. | Shared structured-log schema, failure taxonomy, retryability semantics, and low-cardinality metric model wired across plugin/gateway/skill-service. |
</phase_requirements>

## Summary

Phase 6 should be planned as a reliability-control phase on top of existing stream/persistence contracts, not a protocol rewrite. The codebase already has useful primitives (`runtime.reconnecting`, sequence anomaly detection, gateway delivery retry, turn dedupe) but they are not yet orchestrated into a deterministic reconnect/resume lifecycle for `BRG-04`.

For `DEM-02`, trace metadata already exists in DTOs (`trace_id`, `session_id`, `turn_id`, `seq`) across plugin, gateway, and skill-service, but there is no unified structured logging contract, no cross-service failure taxonomy implementation, and no production-style metrics surface for troubleshooting.

**Primary recommendation:** Plan Phase 6 around four deliverables: (1) plugin reconnect state machine with fresh re-auth and bounded backoff+jitter, (2) resume-anchor and anomaly compensation rules, (3) server-side sendback idempotency keys, and (4) shared observability contract (logs + metrics + taxonomy) with requirement-traceable tests.

## Project Context Checks

- `CLAUDE.md`: not present.
- `.claude/skills` / `.agents/skills`: not present.
- `.planning/config.json`: `workflow.nyquist_validation` is not enabled; skip Validation Architecture section.

## Current Baseline (What Exists)

### Reusable Reliability Assets

- `pc-agent-plugin` runtime already exposes reconnect hook (`onReconnect`) and emits `runtime.reconnecting`.
- Runtime already blocks concurrent in-flight turns and detects `SEQ_ANOMALY` (`seq <= last` or non-contiguous).
- Gateway already has async bounded retry queue (`DeliveryRetryQueue`) and tuple dedupe in forwarder (`session|turn|seq` in-memory set).
- Skill-service persistence already guards duplicate and stale sequence writes in `TurnPersistenceService`.

### Gaps That Must Be Closed in Phase 6

- No reconnect policy engine (attempt budget, backoff+jitter, failure terminalization) in plugin runtime.
- No runtime signal for `resumed`/`failed` reconnect phases; only `runtime.reconnecting` exists.
- No explicit resume-anchor handshake path (`session_id + turn_id + seq`) across plugin<->gateway boundary.
- Sequence anomaly handling logs an error but does not drop duplicates or trigger compensation flow.
- Sendback is not idempotent server-side; `SendbackService` uses random `request_id` and can duplicate IM sends during retries.
- No shared observability schema implementation or metrics dashboard primitives across services.

## Standard Stack

### Core

| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| TypeScript + Vitest (`pc-agent-plugin`) | TS 5.9.x / Vitest 3.2.x | Plugin reconnect orchestration and contract tests | Existing plugin stack and test harness already in place. |
| Java 21 (`gateway`) | JDK 21 | Gateway retry/publish/taxonomy instrumentation | Existing module baseline. |
| Spring Boot + MyBatis (`skill-service`) | Boot 3.4.6 / MyBatis 3.0.4 | Sendback idempotency + observability wiring | Existing service stack and persistence pattern. |
| Existing `trace_id` field contract | Existing protocol | End-to-end correlation key for BRG-04/DEM-02 | Already present across DTOs and APIs, minimizing migration risk. |

### Supporting

| Component | Purpose | When to Use |
|-----------|---------|-------------|
| Exponential backoff with jitter (full-jitter style) | Reconnect thundering-herd reduction and stable retry pacing | Plugin reconnect attempt scheduler. |
| Spring Boot Actuator + Micrometer (Boot-managed) | Standard metrics endpointing and counters/timers | Skill-service metric export (`/actuator/metrics`, optional `/actuator/prometheus`). |
| Micrometer core in gateway (if kept non-Spring) | Uniform metric model with low-cardinality tags | Gateway persistence/bridge/auth failure counters and latency timers. |
| Structured JSON log wrapper per component | Deterministic cross-service troubleshooting fields | All plugin/gateway/skill-service failure and state transitions. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| End-to-end `trace_id` business correlation only | Full distributed tracing rollout in this phase | Full tracing is stronger long-term but larger scope than BRG-04/DEM-02 baseline. |
| DB unique idempotency key for sendback | In-memory duplicate suppression | In-memory does not survive restarts or multi-instance rollout. |
| Bounded retry+jitter | Immediate fail-fast reconnect | Fail-fast degrades UX and violates locked reconnect policy. |

## Architecture Patterns

### Pattern 1: Single-Owner Reconnect Coordinator (Plugin Runtime)
**What:** One session-scoped coordinator owns reconnect attempts and transitions through `reconnecting -> resumed|failed`.  
**When to use:** Any transport disconnect during active session.  
**Key rules:**
- Acquire session reconnect lock; ignore concurrent reconnect triggers for same session.
- Each attempt does fresh auth payload/signature (new nonce/timestamp).
- Retry with exponential backoff + jitter and max-attempt cap.
- Emit deterministic terminal failure payload (`error_code`, `next_action`, `retryable=false`).

### Pattern 2: Resume Anchor + Sequence Compensation
**What:** Resume from `session_id + turn_id + seq` and enforce anomaly policy on inbound events.  
**When to use:** Post-reconnect stream continuation.  
**Key rules:**
- Duplicate `seq` is dropped (no downstream propagation) and logged with taxonomy `bridge`.
- Gap (`incoming > last+1`) triggers replay/compensation request path before accepting new stream.
- Normal continuation resumes only after anchor convergence.

### Pattern 3: Server-Side Sendback Idempotency
**What:** Deterministic idempotency key at skill-service sendback boundary.  
**When to use:** `POST /sessions/{session_id}/sendback` during reconnect/retry windows.  
**Key rules:**
- Build key from stable session context + content fingerprint (hash) + bounded TTL window.
- Add DB unique index on idempotency key; on duplicate return prior send outcome, do not re-send to IM.
- Persist idempotency decision path for diagnostics (`status`, `error_code`, `trace_id`).

### Pattern 4: Shared Observability Contract
**What:** Cross-service schema for logs + metrics + taxonomy.  
**When to use:** All reconnect, persistence, and sendback state transitions and failures.  
**Minimum contract:**
- Log fields: `tenant_id`, `client_id`, `session_id`, `turn_id`, `seq`, `trace_id`, `error_code`, `component`, `status`, `failure_class`, `retryable`.
- Failure classes: `auth`, `bridge`, `persistence`, `sendback`, `unknown`.
- Metrics (low cardinality labels only): reconnect attempts/outcomes, resume anomalies, persistence retry outcomes, sendback dedupe hits, sendback failures by class.

### Anti-Patterns to Avoid

- Multiple reconnect owners per session.
- Continuing stream delivery after detected gap without compensation.
- High-cardinality metric labels (`trace_id`, raw `session_id`, raw message text).
- Taxonomy drift where each module invents different failure-class strings.
- Logging payload content containing sensitive assistant/user text instead of safe metadata.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Reconnect retry timing | Ad-hoc sleep loops without jitter | Bounded exponential backoff + jitter policy component | Predictable behavior under burst disconnects and safer rollout tuning. |
| Metrics registry/export | Custom in-memory counter endpoints | Micrometer/Actuator-compatible metrics surface | Standard integration path for dashboarding and alerts. |
| Sendback duplicate protection | Per-process maps | DB-backed unique idempotency key | Survives restart and supports multi-instance correctness. |
| Trace propagation format | Per-hop custom renaming | Stable `trace_id` contract (optionally aligned to W3C trace headers at boundaries) | Keeps cross-service correlation deterministic and debuggable. |

## Common Pitfalls

### Pitfall 1: Reconnect Event Without Real Transport Recovery
**What goes wrong:** UI sees `runtime.reconnecting` but session never truly resumes.  
**Why it happens:** Reconnect hook is emitted without connect/auth/resume orchestration.  
**How to avoid:** Gate `resumed` on verified reconnect + fresh auth + anchor sync.

### Pitfall 2: Duplicate/Gap Events Leak Into UI/Sendback
**What goes wrong:** Post-reconnect stream duplicates or missing content create incorrect user-visible timeline and duplicate sendback attempts.  
**Why it happens:** Sequence anomaly is observed but not enforced.  
**How to avoid:** Drop duplicates, compensate gaps, and only continue on contiguous sequence.

### Pitfall 3: Idempotency Implemented Client-Side Only
**What goes wrong:** Client retries or reconnects still trigger duplicate IM sends.  
**Why it happens:** Client state is volatile and not authoritative.  
**How to avoid:** Server-side idempotency key with unique index and deterministic duplicate response.

### Pitfall 4: Metric Cardinality Explosion
**What goes wrong:** Metrics backend cost/performance collapse and unusable dashboards.  
**Why it happens:** Dynamic labels such as `trace_id` or full `session_id` used as tags.  
**How to avoid:** Keep tags to low-cardinality dimensions (`component`, `failure_class`, `outcome`), keep correlation IDs in logs only.

### Pitfall 5: Taxonomy Drift Across Modules
**What goes wrong:** One failure appears under different class names per service, breaking triage and alerting.  
**Why it happens:** No shared enum/contract tests.  
**How to avoid:** Central taxonomy constants and contract tests in plugin/gateway/skill-service.

## Code Examples

### Example 1: Plugin Reconnect Policy Skeleton (TypeScript)
```typescript
for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
  emitState("reconnecting", { attempt });
  try {
    const auth = buildFreshAuth(); // new nonce + timestamp per attempt
    await transport.connect(auth);
    await transport.resume({ session_id, turn_id, seq });
    emitState("resumed", { attempt });
    return;
  } catch (error) {
    if (attempt === maxAttempts) {
      emitTerminal("BRIDGE_RECONNECT_FAILED", "Open session again from chat.");
      return;
    }
    await sleep(fullJitterDelay(attempt, baseMs, capMs));
  }
}
```

### Example 2: Sendback Idempotency Gate (Java + MyBatis pattern)
```java
String idempotencyKey = hash(sessionId + "|" + turnId + "|" + conversationId + "|" + selectedFingerprint);
Optional<SendbackRecord> existing = sendbackRecordMapper.findByIdempotencyKey(idempotencyKey);
if (existing.isPresent()) {
    return toResponse(existing.get()); // deterministic duplicate-safe return
}
// continue IM send and persist with unique key
```

### Example 3: Structured Failure Log Envelope
```json
{
  "component": "skill-service.sendback",
  "status": "failed",
  "failure_class": "sendback",
  "retryable": true,
  "error_code": "IM_CHANNEL_UNAVAILABLE",
  "tenant_id": "tenant-x",
  "client_id": "client-x",
  "session_id": "session-x",
  "turn_id": "turn-x",
  "seq": 42,
  "trace_id": "trace-x"
}
```

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Emit reconnect signal only | Coordinated reconnect state machine with bounded retries and terminal semantics | Deterministic BRG-04 behavior and cleaner UX/ops diagnosis. |
| Duplicate suppression by ad-hoc process memory | Persistent idempotency key for sendback and replay-safe anchor logic | Prevents duplicate IM sends in retry/reconnect windows. |
| Unstructured logs and scattered status checks | Shared log schema + failure taxonomy + low-cardinality metrics | Enables DEM-02 cross-service tracing and alertability. |

## Open Questions

1. **Gateway resume contract shape**
   - What we know: Resume anchor must be `session_id + turn_id + seq`.
   - What's unclear: Whether resume is an explicit topic/API or piggybacked on existing stream topic.
   - Recommendation: Lock one explicit resume contract in Wave 0 plan task.

2. **Idempotency window retention strategy**
   - What we know: Window TTL is discretionary.
   - What's unclear: Exact TTL and cleanup mechanism (scheduled purge vs partition retention).
   - Recommendation: Choose TTL with production replay expectations and add migration/test for cleanup behavior.

3. **Metrics export path for gateway module**
   - What we know: Gateway currently is not a full Spring Boot service module.
   - What's unclear: Whether gateway metrics should be embedded via Micrometer core only or exposed through hosting runtime.
   - Recommendation: Decide integration boundary early to avoid rework in DEM-02 dashboard tasks.

4. **Trace context interoperability**
   - What we know: `trace_id` is current canonical field and must stay.
   - What's unclear: Whether to add W3C `traceparent` at HTTP/WS boundaries now or defer.
   - Recommendation: Keep `trace_id` mandatory; treat W3C header mapping as optional compatibility enhancement if low-risk.

## Sources

### Primary (HIGH confidence)
- `.planning/phases/06-reliability-observability-hardening/06-CONTEXT.md` (locked decisions/scope)
- `.planning/REQUIREMENTS.md` (`BRG-04`, `DEM-02` definitions)
- `.planning/ROADMAP.md` (Phase 6 success criteria)
- `.planning/STATE.md` (phase sequencing/status)
- `pc-agent-plugin/src/core/runtime/BridgeRuntime.ts`
- `pc-agent-plugin/src/host-adapter/HostEventBridge.ts`
- `gateway/src/main/java/com/chatcui/gateway/persistence/*`
- `gateway/src/main/java/com/chatcui/gateway/runtime/BridgePersistencePublisher.java`
- `skill-service/src/main/java/com/chatcui/skill/service/*`
- `web-demo/src/*`

### Secondary (MEDIUM confidence, official docs)
- AWS Architecture Blog: Exponential Backoff and Jitter  
  https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
- W3C Trace Context Recommendation (`traceparent`, `tracestate`)  
  https://www.w3.org/TR/trace-context/
- Micrometer concepts: naming and cardinality guidance  
  https://docs.micrometer.io/micrometer/reference/concepts/naming.html  
  https://docs.micrometer.io/micrometer/reference/observation/introduction.html
- Spring Boot Actuator observability/tracing/metrics endpoints  
  https://docs.spring.io/spring-boot/reference/actuator/observability.html  
  https://docs.spring.io/spring-boot/reference/actuator/tracing.html  
  https://docs.spring.io/spring-boot/reference/actuator/metrics.html
- Prometheus instrumentation best-practices (label cardinality)  
  https://prometheus.io/docs/practices/instrumentation/
- OpenTelemetry Java propagation defaults  
  https://opentelemetry.io/docs/languages/java/exporters/

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- Requirement mapping and gap analysis: HIGH (direct code + planning artifacts)
- Reliability architecture recommendations: HIGH (locked decisions + existing primitives)
- Observability stack choices: MEDIUM-HIGH (official docs + local stack constraints)

**Research date:** 2026-03-04  
**Valid until:** 2026-04-03 (or until phase decisions/scope change)
