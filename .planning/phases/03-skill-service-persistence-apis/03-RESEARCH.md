# Phase 3: Skill Service Persistence APIs - Research

**Researched:** 2026-03-04  
**Domain:** Gateway-to-Skill persistence flow, turn-history APIs, MySQL 5.7 schema/index compatibility  
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

### Carried Forward (Locked from prior phases)
- Plugin-first architecture remains fixed; PC agent bridge contract is unchanged.
- Stream contract remains `delta + final + completed` with `session_id`, `turn_id`, `seq`, `trace_id` and `snake_case` fields.
- Session runtime keeps one in-flight turn policy and structured error event pattern.

### Conversation Persistence Model
- Persistence model is **turn-summary primary** (not standalone event-log primary).
- Delta events are persisted by **incrementally updating the same turn snapshot** (no separate delta row requirement).
- Actor model uses fixed enum baseline (`user`, `assistant`, `system`, `plugin`) with extension capacity.
- Metadata baseline per persisted turn includes at least:
  - `tenant_id`, `client_id`, `session_id`, `turn_id`, `seq`, `trace_id`
  - `actor`, `event_type`, `created_at`
- Write policy is event-driven update: each streaming event can update current turn state/snapshot.

### History Query API Contract
- API shape uses session resource style `GET` endpoint for history retrieval.
- Default ordering is replay-friendly ascending order.
- Pagination baseline is **turn-based** (not event-based pagination).
- Query item returns turn `status` (`in_progress` / `completed` / `error`) and current content snapshot.
- Response payload follows `snake_case` naming.

### Gateway to Skill Delivery Semantics
- Gateway uses dual-stage semantics: receive acknowledgment and persistence-status reporting are separated.
- Skill-unavailable behavior is **non-blocking async retry**, not silent log-only drop and not fail-fast session termination.
- Delivery idempotency baseline uses `session_id + turn_id + seq`.
- History/query visibility includes `delivery_status` (e.g., `pending` / `saved` / `failed`) for client-side state awareness.

### Claude's Discretion
- Exact REST path prefix and parameter names while preserving the decided API shape.
- Exact status literal sets and error code literals under the decided semantics.
- DTO field ordering and schema split (request/response object boundaries) as long as decided fields are preserved.

### Deferred Ideas (OUT OF SCOPE)
None - discussion stayed within Phase 3 scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SVC-01 | AI-Gateway transparently forwards OpenCode streaming/output events to Skill service. | Define gateway forwarding contract around existing `skill.turn.*` topics, dual-stage delivery status semantics, idempotent delivery key, and async retry behavior. |
| SVC-02 | Skill service persists full conversation history with session and actor metadata. | Define turn-summary write model, upsert strategy, metadata schema, status transitions, and MySQL 5.7-safe indexes for ordered replay/query. |
| SVC-03 | Skill client can query persisted session history from Skill service APIs. | Define session-resource GET history API, ascending deterministic order, turn-based pagination baseline, and response DTO with `status` + snapshot + `delivery_status`. |
</phase_requirements>

## Summary

Phase 3 should be planned as a foundational backend phase that introduces a dedicated Skill persistence boundary and makes it authoritative for chat-history reads. The established stream contract from Phase 2 (`delta`, `final`, `completed`, identifiers in snake_case) should be reused directly rather than transformed into a second internal shape.

The strongest implementation path is: gateway receives stream-aligned events, forwards them immediately to Skill service, Skill service performs idempotent upsert-on-turn using `session_id + turn_id + seq`, and query API returns ordered turn snapshots with `delivery_status`. This matches all locked decisions while keeping Phase 3 scope clear of UI/sendback/reliability hardening concerns.

Current codebase reality must be planned explicitly: repository backend code is currently gateway-auth focused plain Java classes with JUnit tests, while project baseline expects Spring Boot + MVC + MyBatis + MySQL 5.7. Phase planning must include bootstrap tasks (module and dependency wiring, persistence infrastructure, schema migration path, integration tests) before feature logic.

**Primary recommendation:** Plan Phase 3 in two tracks: (1) bootstrap Skill service + DB schema/integration tests, then (2) implement gateway forwarder + idempotent turn persistence + ordered history API on top of the existing stream contract.

## Project Context Checks

- `CLAUDE.md`: not found at repo root.
- `.claude/skills/` and `.agents/skills/`: not present.
- Planning implication: no additional repo-local skill/policy layer constrains this phase beyond provided context and `.planning/*` artifacts.

## Standard Stack

### Core

| Library/Component | Version | Purpose | Why Standard |
|-------------------|---------|---------|--------------|
| JDK | 21 (fixed in repo) | Runtime baseline for gateway/skill services | Already fixed in root/gateway Maven configuration. |
| Spring Boot | 3.4.6 (project baseline) | HTTP APIs, dependency management, service bootstrapping | Project-level fixed backend baseline in `.planning/PROJECT.md`; consistent with existing phase assumptions. |
| Spring MVC | Boot-managed | Session history GET API and gateway forwarding endpoints/clients | Matches project baseline and existing backend direction. |
| MyBatis Spring Boot Starter | 3.0.x line | Mapper-based SQL access and explicit query control | Official support matrix covers Spring Boot 3.2-3.5 and Java 17+; suitable for Boot 3.4.6 baseline. |
| MySQL | 5.7 | Turn persistence store and indexed history retrieval | Explicit project constraint and phase success criterion. |

### Supporting

| Library/Component | Purpose | When to Use |
|-------------------|---------|-------------|
| MySQL Connector/J | JDBC driver for MyBatis | Required for Skill service DB connectivity. |
| Maven Surefire (repo already uses 3.5.2 in gateway) | Fast unit/integration test execution | Keep test gates aligned with existing module style. |
| Structured logging + trace propagation | Preserve `trace_id` across gateway->skill writes | Needed for SVC-01/SVC-02 diagnostics and later DEM-02 hardening. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Turn-summary primary persistence | Full append-only event log as primary read model | Better forensic granularity, but conflicts with locked decision and increases Phase 3 complexity. |
| MyBatis explicit SQL | Spring Data JPA/Hibernate | Faster CRUD scaffolding, but weaker control over MySQL 5.7 query/index tuning and ordered replay SQL. |
| Offset-only pagination | Cursor/keyset pagination baseline | Offset is simpler initially but degrades with deeper pages and is less deterministic under concurrent writes. |

**Installation (module-level target for Phase 3 bootstrap):**
```bash
# Maven dependencies to add in backend module(s)
org.springframework.boot:spring-boot-starter-web
org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.x
com.mysql:mysql-connector-j
```

## Architecture Patterns

### Recommended Project Structure

```text
gateway/
  src/main/java/com/chatcui/gateway/
    persistence/
      SkillPersistenceForwarder.java        # gateway -> skill forwarding client/service
      DeliveryRetryQueue.java               # async retry policy abstraction
    events/
      SkillTurnEvent.java                   # normalized event contract for forwarding

skill-service/                              # new module expected in this phase
  src/main/java/com/chatcui/skill/
    api/
      SessionHistoryController.java         # GET /sessions/{session_id}/history
    service/
      TurnPersistenceService.java           # idempotent upsert logic
      SessionHistoryQueryService.java       # ordered history reads
    persistence/
      mapper/TurnRecordMapper.java
      model/TurnRecord.java
  src/main/resources/
    db/migration/                           # schema/index DDL scripts
    mybatis/
      TurnRecordMapper.xml
```

### Pattern 1: Gateway Forwarder with Dual-Stage Delivery Semantics
**What:** Gateway emits immediate receive-ack semantics independently from persistence outcome, and reports persistence status asynchronously.
**When to use:** Every outbound `skill.turn.*` event received from plugin stream path.
**Implementation notes:**
- Forward immediately on event arrival (`near real time`).
- Persist delivery-attempt record keyed by `session_id + turn_id + seq`.
- Retry asynchronously when Skill service is unavailable; do not terminate session path.
- Publish `delivery_status` transitions: `pending -> saved|failed`.

### Pattern 2: Turn-Snapshot Upsert Model
**What:** One logical turn row is incrementally updated by delta/final/completed/error stream events.
**When to use:** Skill service write side for SVC-02.
**Implementation notes:**
- Primary identity: `(tenant_id, client_id, session_id, turn_id)`.
- Monotonic sequence guard: only apply update when incoming `seq` is newer.
- Delta updates append or merge content snapshot; final/completed set terminal fields.
- Keep `event_type`, `status`, `delivery_status`, `updated_at` for replay and troubleshooting.

### Pattern 3: Deterministic Ascending History Query
**What:** Session history endpoint returns replay-safe ascending order with turn-based pagination.
**When to use:** `GET /sessions/{session_id}/history` path.
**Implementation notes:**
- Stable sort key must be deterministic: `ORDER BY created_at ASC, turn_id ASC` (or `seq` where appropriate).
- Include tie-break column to avoid nondeterministic ordering with LIMIT.
- Pagination token should encode last seen turn key (keyset baseline), even if page/size params are also supported for compatibility.

### Pattern 4: MySQL 5.7-Safe Schema and Indexing
**What:** Design schema/indexes around 5.7 constraints, not 8.0-only features.
**When to use:** DDL and query-plan verification tasks.
**Implementation notes:**
- Keep indexed string columns within safe byte budgets for utf8mb4.
- Do not rely on descending index behavior from MySQL 8.0+.
- For searchable JSON attributes, use generated columns + index (5.7-supported pattern).

### Anti-Patterns to Avoid

- Using separate schemas/contracts for gateway forwarding vs history query DTOs.
- Treating persistence failures as silent drops (violates delivery visibility decision).
- Relying only on offset pagination without deterministic tie-breaks.
- Using MySQL 8.0-only SQL/index assumptions while target is 5.7.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Idempotent insert/update race handling | Custom application-level dedupe maps | MySQL unique key + `INSERT ... ON DUPLICATE KEY UPDATE` | Atomic at DB layer, simpler and safer under concurrency. |
| SQL session factory wiring | Manual `SqlSessionFactory` bootstrapping everywhere | MyBatis Spring Boot starter auto-configuration | Standardized wiring, less boilerplate and drift. |
| JSON filter indexing hacks | App-side JSON parsing and in-memory filtering | Generated columns + indexes in MySQL 5.7 | Keeps query work in DB, predictable performance. |
| Ad-hoc retry loops | Unbounded in-thread sleeps/retries | Bounded async retry policy with delivery state tracking | Prevents session-path blocking and hidden failures. |

**Key insight:** Use database-enforced idempotency and deterministic query ordering as core correctness guarantees; avoid moving these guarantees into custom in-memory logic.

## Common Pitfalls

### Pitfall 1: Non-Deterministic History Order
**What goes wrong:** Same session page returns turns in different order between requests.
**Why it happens:** ORDER BY does not fully break ties when LIMIT is used.
**How to avoid:** Always sort by deterministic composite key (`created_at`, then `turn_id` or another unique tie-breaker).
**Warning signs:** Flaky pagination tests; replay mismatch on identical data.

### Pitfall 2: Duplicate or Regressed Turn Snapshots
**What goes wrong:** Older `seq` overwrites newer turn snapshot, or duplicate events create inconsistent state.
**Why it happens:** Missing idempotency key and monotonic sequence guard.
**How to avoid:** Enforce unique key on delivery idempotency tuple and update only when incoming `seq` is newer.
**Warning signs:** `seq` decreases in stored rows; repeated events change final content unexpectedly.

### Pitfall 3: MySQL 5.7 Index Width Overruns
**What goes wrong:** Index creation fails or is truncated due utf8mb4 byte limits.
**Why it happens:** Large VARCHAR columns indexed without byte-budget checks.
**How to avoid:** Keep indexed text keys constrained (e.g., 191-char safe patterns where needed), verify row format/index prefix behavior in DDL tests.
**Warning signs:** Migration failures in CI against MySQL 5.7.

### Pitfall 4: Assuming 8.0 Index Features in 5.7
**What goes wrong:** Query plans and ordering performance differ from expectations.
**Why it happens:** Descending index definitions are treated with older behavior in pre-8.0 line.
**How to avoid:** Design indexes and SQL for 5.7 semantics; validate with `EXPLAIN` on 5.7 test instance.
**Warning signs:** Slow history queries despite index definitions that appear correct on paper.

### Pitfall 5: Hidden Delivery Failures
**What goes wrong:** User thinks turn is persisted, but storage failed and no state is exposed.
**Why it happens:** Gateway logs errors without state propagation.
**How to avoid:** Persist and expose `delivery_status`, and include failure reason code in internal diagnostics.
**Warning signs:** Missing turns with no client-visible status difference.

## Code Examples

Verified implementation patterns from target stack and MySQL 5.7 constraints:

### Idempotent Upsert for Stream Event Delivery
```sql
INSERT INTO skill_turn_delivery (
  tenant_id, client_id, session_id, turn_id, seq,
  trace_id, event_type, delivery_status, created_at, updated_at
) VALUES (
  #{tenantId}, #{clientId}, #{sessionId}, #{turnId}, #{seq},
  #{traceId}, #{eventType}, 'pending', NOW(6), NOW(6)
)
ON DUPLICATE KEY UPDATE
  trace_id = VALUES(trace_id),
  event_type = VALUES(event_type),
  updated_at = NOW(6);
```

### Monotonic Turn Snapshot Update Guard
```sql
UPDATE skill_turn_snapshot
SET
  content_snapshot = #{contentSnapshot},
  status = #{status},
  event_type = #{eventType},
  seq = #{seq},
  delivery_status = #{deliveryStatus},
  updated_at = NOW(6)
WHERE tenant_id = #{tenantId}
  AND client_id = #{clientId}
  AND session_id = #{sessionId}
  AND turn_id = #{turnId}
  AND seq <= #{seq};
```

### Replay-Friendly Session History Query (Turn-Based)
```sql
SELECT
  tenant_id,
  client_id,
  session_id,
  turn_id,
  actor,
  status,
  delivery_status,
  content_snapshot,
  seq,
  trace_id,
  created_at,
  updated_at
FROM skill_turn_snapshot
WHERE tenant_id = #{tenantId}
  AND client_id = #{clientId}
  AND session_id = #{sessionId}
  AND (
    (created_at > #{cursorCreatedAt})
    OR (created_at = #{cursorCreatedAt} AND turn_id > #{cursorTurnId})
  )
ORDER BY created_at ASC, turn_id ASC
LIMIT #{pageSize};
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Full history reads driven from append-only event log for every client query | Turn-snapshot read model with incremental updates from stream events | Context decision for Phase 3 | Faster reads and simpler session replay for UI, while keeping stream alignment. |
| Offset-only pagination without deterministic tie-break | Deterministic turn-based pagination with stable ordering keys | MySQL optimizer/order behavior best-practice | Prevents inconsistent page boundaries and replay drift. |
| Implicit persistence success assumptions | Explicit `delivery_status` lifecycle (`pending/saved/failed`) | Locked delivery semantics | User and support teams gain visibility into eventual consistency and retry outcomes. |

**Deprecated/outdated for this phase:**
- Treating Skill persistence as optional side-effect: conflicts with Phase 3 source-of-truth goal.
- Designing around MySQL 8.0-only index semantics: target is explicitly MySQL 5.7.

## Open Questions

1. **Module boundary choice: augment existing `gateway` vs add separate `skill-service` module now**
   - What we know: Context says Skill persistence module is not present yet and must become source of truth.
   - What's unclear: Whether this phase should physically introduce a new Maven module immediately or stage inside gateway package first.
   - Recommendation: Plan a Wave 0 decision task; prefer explicit `skill-service` module to reduce later Phase 4/5 coupling.

2. **Delivery-status propagation contract to client-facing layers**
   - What we know: `delivery_status` must be query-visible.
   - What's unclear: Exact error code/status vocabulary beyond baseline examples.
   - Recommendation: Lock literal set during planning and add contract tests before implementation.

3. **Pagination contract shape**
   - What we know: session-resource GET, ascending order, turn-based pagination.
   - What's unclear: whether v1 response includes page number, cursor token, or both.
   - Recommendation: Choose cursor-first baseline with optional page-size param; keep DTO forward-compatible.

4. **Schema migration mechanism in this repository**
   - What we know: no DB migration tooling is currently present in code.
   - What's unclear: whether to adopt Flyway/Liquibase now or use controlled SQL scripts during this phase.
   - Recommendation: Decide in planning Wave 0 and tie to CI MySQL 5.7 verification command.

## Sources

### Primary (HIGH confidence)
- Local phase context: `.planning/phases/03-skill-service-persistence-apis/03-CONTEXT.md`
- Local requirements: `.planning/REQUIREMENTS.md`
- Local state/history: `.planning/STATE.md`
- Project baseline stack/constraints: `.planning/PROJECT.md`
- Current repo build/module reality: `pom.xml`, `gateway/pom.xml`, `gateway/src/main/java/...`, `pc-agent-plugin/src/core/...`
- MyBatis Spring Boot starter requirements and auto-config behavior: https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/
- MyBatis release baseline for Spring Boot 3.4 line: https://github.com/mybatis/spring-boot-starter/releases
- Spring Boot 3.4.6 release confirmation: https://spring.io/blog/2025/05/22/spring-boot-3-4-6-available-now
- MySQL 5.7 InnoDB row format and index-prefix limits: https://dev.mysql.com/doc/refman/5.7/en/innodb-row-format.html
- MySQL 5.7 JSON type details: https://dev.mysql.com/doc/refman/5.7/en/json.html
- MySQL 5.7 generated columns and indexing pattern: https://dev.mysql.com/doc/refman/5.7/en/create-table-generated-columns.html
- MySQL LIMIT/ORDER BY determinism notes: https://dev.mysql.com/doc/refman/5.7/en/limit-optimization.html
- MySQL idempotent upsert primitive: https://dev.mysql.com/doc/refman/5.7/en/insert-on-duplicate.html
- MySQL temporal precision (`DATETIME/TIMESTAMP(6)`): https://dev.mysql.com/doc/refman/5.7/en/date-and-time-type-syntax.html

### Secondary (MEDIUM confidence)
- None.

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - fixed by project docs + official compatibility docs.
- Architecture patterns: HIGH - directly constrained by locked phase decisions and existing stream contracts.
- Pitfalls: HIGH - validated against official MySQL 5.7 docs plus current repo constraints.

**Research date:** 2026-03-04  
**Valid until:** 2026-04-03 (or earlier if backend stack baseline changes)
