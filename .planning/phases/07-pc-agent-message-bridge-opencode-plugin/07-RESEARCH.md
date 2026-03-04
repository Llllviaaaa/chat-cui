# Phase 07: PC Agent Plugin Architecture Alignment - Research

**Researched:** 2026-03-04  
**Domain:** Architecture alignment closure and governance hardening for `pc-agent-plugin` + gateway auth/resume contracts  
**Confidence:** HIGH (repo-local evidence), MEDIUM (reference-repo delta visibility)

<user_constraints>
## User Constraints (from 07-CONTEXT.md)

### Locked Decisions
- Phase 07 is an **alignment closure phase**, not a net-new capability phase.
- Scope is fixed to `pc-agent-plugin` and gateway-facing runtime/auth/resume contracts.
- Out of scope: web-demo feature expansion, skill-service feature expansion, and mobile parity.
- Completion requires an **alignment baseline package** containing:
  - requirement mapping,
  - explicit gap-closed checklist,
  - acceptance scripts/commands,
  - runbook updates.
- Compatibility policy is strict by default:
  - backward compatibility,
  - additive-only contract evolution,
  - explicit contract version signal on key events.
- If deprecation is unavoidable, use a **two-release window** before removal.
- Hard gate is required: no-drift + plugin host integration + CLI real-chain path + gateway auth/resume regressions.
- Acceptance evidence must record command/date/result and correlation identifiers (`session_id`, `trace_id`).
- Gate failures block release unless there is a temporary waiver with owner + expiration.
- Carry forward Phase 01.1 policy: internal versioning is primary; no mandatory continuous upstream sync.

### Claude's Discretion
- Contract version field naming/placement.
- Exact gateway regression command composition (as long as auth/resume coverage is preserved).
- Waiver template schema.
- Runbook/evidence formatting.
</user_constraints>

## Planning-Critical Findings

1. **Phase 07 must be planned as closure/governance work, not runtime feature work.**  
   The core architecture already exists from Phases 01.1/02/06; planning should target proof, guardrails, and policy lock-in.

2. **Roadmap requirement IDs for Phase 07 are still `TBD`.**  
   Planning quality will be weak unless Phase 07 starts by defining provisional `P07-*` IDs mapped to existing v1 requirements (`BRG-*`, `AUT-*`, `DEM-02`).

3. **Hard-gate requirements are partially implemented but not consolidated into one Phase 07 gate.**  
   Tests/scripts exist across `pc-agent-plugin` and `gateway`, but there is no single `verify:phase-07` command or dedicated CI workflow equivalent to 01.1 closure rigor.

4. **Contract version signaling is inconsistent.**  
   `ProtocolBridge` already uses `protocol_version=bridge.v1`, but host runtime event envelopes (`runtime.*`, `gateway.*` at host boundary) do not currently expose an explicit contract-version signal.

5. **Governance artifacts required by 07-CONTEXT do not yet exist.**  
   No explicit Phase 07 gap-closed checklist, no Phase 07 requirement mapping file, and no waiver template with owner/expiration were found.

6. **Reference-repository delta cannot be proven yet from this workspace alone.**  
   No local checkout of `message-bridge-opencode-plugin` exists, so alignment claims need a documented baseline-snapshot method in the plan.

7. **Phase 6 observability and Phase 01.1 no-drift decisions are reusable and should be inherited, not redesigned.**  
   Reuse existing failure taxonomy, low-cardinality metric constraints, evidence format, and no-drift ownership checks.

8. **Scope creep risk is high if plan tasks are not explicitly bounded.**  
   True secure-store reimplementation, new transport protocols, or continuous upstream sync automation are not required to close this phase unless a concrete gap is proven.

## Phase Requirement ID Suggestions (Replace `TBD`)

| Suggested ID | Intent | Maps To | Completion Signal |
|---|---|---|---|
| P07-BASE-01 | Alignment baseline package exists and is complete (mapping/checklist/commands/runbook) | BRG-01, BRG-03, BRG-04, DEM-02 | 07 package docs present and reviewed |
| P07-COMPAT-01 | Host/plugin/gateway contracts remain backward compatible and additive-only | BRG-01, BRG-03, BRG-04 | Compatibility contract tests + no breaking payload removals |
| P07-VERSION-01 | Key runtime events expose explicit contract version signal | BRG-03 | Host boundary events include version field; tests assert it |
| P07-GATE-01 | Full-chain hard gate is executable and release-blocking by default | BRG-04, AUT-02, AUT-03, DEM-02 | Single gate command/workflow green |
| P07-EVID-01 | Acceptance evidence records command/date/result/session_id/trace_id | DEM-02 | 07-ACCEPTANCE-EVIDENCE.md complete and reproducible |
| P07-GOV-01 | Waiver/deprecation governance policy is documented and enforceable | BRG-04, AUT-03 | waiver template + two-release policy captured in runbook |

## Current Baseline (What Already Exists)

### Reusable Assets
- Plugin shared runtime factory and layered architecture:
  - `pc-agent-plugin/src/core/runtime/BridgeRuntimeFactory.ts`
  - `pc-agent-plugin/src/host-adapter/HostPluginAdapter.ts`
  - `pc-agent-plugin/src/cli/runtime/CliRuntimeBootstrap.ts`
- Bridge protocol versioning already present on gateway message payloads:
  - `pc-agent-plugin/src/core/bridge/ProtocolBridge.ts`
- No-drift checks and architecture tests already in place:
  - `pc-agent-plugin/scripts/validate-no-drift.mjs`
  - `pc-agent-plugin/test/architecture/*`
- Gateway resume-owner/sequence policy is implemented and tested:
  - `gateway/src/main/java/com/chatcui/gateway/runtime/ResumeCoordinator.java`
  - `gateway/src/test/java/com/chatcui/gateway/runtime/ResumeCoordinatorTest.java`
- Prior closure artifact patterns exist:
  - `01.1-ACCEPTANCE-EVIDENCE.md`, `01.1-VERIFICATION.md`
  - `06-ACCEPTANCE-EVIDENCE.md`, `06-OBSERVABILITY-BASELINE.md`

### Gaps to Close in Phase 07
- No Phase 07 requirement mapping/checklist/evidence/runbook files yet.
- No explicit host-boundary contract version field for runtime event envelopes.
- No Phase 07 consolidated gate command/workflow combining plugin + gateway checks.
- No documented waiver template (owner + expiration) for temporary gate bypass.
- No documented two-release deprecation workflow tied to contract fields/events.
- No documented reference-baseline snapshot process against `message-bridge-opencode-plugin`.

## Standard Stack

### Core
| Component | Why Use It in Phase 07 |
|---|---|
| TypeScript + Vitest in `pc-agent-plugin` | Existing place for host/CLI/runtime contract enforcement and no-drift coverage |
| Java 21 + Maven tests in `gateway` | Existing auth/resume regression suite and deterministic error behavior |
| Existing planning artifact pattern under `.planning/phases/*` | Proven auditability format for evidence/mapping/verification |

### Supporting
| Component | Purpose |
|---|---|
| Existing `verify:no-drift` script | Base gate for architecture ownership and shared runtime factory usage |
| Existing auth doc-sync/regression tests (`gateway`) | Prevent auth contract drift while alignment hardens |
| Existing observability schema from Phase 6 | Preserve traceability and low-cardinality constraints in Phase 07 evidence |

## Architecture Patterns

### Pattern 1: Alignment-as-Governance (Not Feature Delivery)
Plan tasks to produce enforceable artifacts and verification commands, not new product behavior.

### Pattern 2: Additive Compatibility with Explicit Version Signals
Add contract-version metadata without removing existing fields or changing existing semantics.

### Pattern 3: Cross-Module Hard Gate Composition
Treat plugin and gateway suites as one release gate for this phase, with a single documented command flow.

### Pattern 4: Evidence-First Closure
Every alignment claim should map to executable command output plus `session_id`/`trace_id` evidence records.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---|---|---|
| Architecture drift detection | New custom framework | Extend existing `validate-no-drift.mjs` + architecture tests |
| Contract regression policy | Ad hoc review checklist only | Typed tests + documented compatibility policy + evidence log |
| Full-chain validation | Manual one-off local runs | Scripted gate command(s) and CI workflow entrypoint |
| Upstream sync strategy | Continuous auto-sync pipeline | Internal versioning + explicit baseline snapshot notes per release candidate |

## Common Pitfalls

1. **Re-implementing architecture instead of closing alignment gaps**  
   Causes scope creep and duplicates 01.1 work.

2. **Making breaking payload changes while claiming "alignment"**  
   Violates additive-only policy and destabilizes host/client compatibility.

3. **Claiming reference alignment without reproducible baseline evidence**  
   Without a documented snapshot/diff process, alignment becomes subjective.

4. **Using only plugin tests and skipping gateway auth/resume regressions**  
   Misses half of Phase 07 hard gate requirements.

5. **Allowing waivers without ownership/expiration**  
   Converts temporary bypasses into permanent risk.

6. **Adding high-cardinality identifiers into metrics while hardening gates**  
   Regresses Phase 6 observability constraints.

## Recommended Plan Decomposition

| Wave | Goal | Deliverables |
|---|---|---|
| Wave 0 | Lock scope + IDs + closure definition | `P07-*` IDs, phase-07 requirement mapping skeleton, explicit in/out scope |
| Wave 1 | Contract compatibility + version signaling | Additive version field policy, host/runtime contract tests, deprecation policy doc |
| Wave 2 | Full-chain hard gate assembly | `verify:phase-07` command plan (plugin + gateway), CI workflow draft, waiver process |
| Wave 3 | Baseline package completion | gap-closed checklist, acceptance evidence table, runbook updates |
| Wave 4 | Verification + tracker sync | Phase 07 verification report and roadmap/state/requirements updates |

## Suggested Phase 07 Artifacts

Create:
- `.planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-REQUIREMENT-MAPPING.md`
- `.planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-GAP-CLOSED-CHECKLIST.md`
- `.planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-ACCEPTANCE-EVIDENCE.md`
- `.planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-ALIGNMENT-RUNBOOK.md`
- `.planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-WAIVER-TEMPLATE.md`

Update:
- `pc-agent-plugin/package.json` (add `verify:phase-07` composition command)
- `pc-agent-plugin/src/host-adapter/contracts/HostPluginContract.ts` (version signal shape, additive)
- `pc-agent-plugin/test/integration/host/HostEventContract.integration.test.ts` (compat/version assertions)
- `.github/workflows/*` (phase-07 hard-gate workflow)

## Candidate Hard-Gate Command Set (for planning)

Plugin side:
- `npm.cmd --prefix pc-agent-plugin run verify:no-drift`
- `npm.cmd --prefix pc-agent-plugin run test:plugin-mode-integration`
- `npm.cmd --prefix pc-agent-plugin run test:cli-real-chain`

Gateway side:
- `./mvnw.cmd -pl gateway "-Dtest=AuthServiceTest,ErrorResponseFactoryTest,WsAuthHandshakeInterceptorTest,ResumeCoordinatorTest,BridgePersistencePublisherTest,AuthEntryIntegrationTest,WsAuthFailureIntegrationTest" test`

Planning note: final command composition can vary, but it must still prove auth + resume + host integration + no-drift + CLI real-chain.

## Open Questions (Must Resolve Before Detailed Planning)

1. What exact repository snapshot or tag of `message-bridge-opencode-plugin` should be treated as the Phase 07 baseline?
2. Which host events are "key events" that must carry explicit contract version signal?
3. Where should deprecation policy live as source of truth (phase runbook vs global contract doc)?
4. Should waiver approvals be file-backed in repo (auditable) or external process only?
5. Is the target to add a dedicated `phase-07` CI workflow or extend existing workflow strategy?

## Sources

Primary (repo-local, high confidence):
- `.planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-CONTEXT.md`
- `.planning/REQUIREMENTS.md`
- `.planning/STATE.md`
- `.planning/ROADMAP.md`
- `.planning/phases/01.1-pc-agent-plugin-architecture-alignment-with-message-bridge-opencode-plugin/01.1-RESEARCH.md`
- `.planning/phases/01.1-pc-agent-plugin-architecture-alignment-with-message-bridge-opencode-plugin/01.1-ARCHITECTURE-DECISION-RECORD.md`
- `.planning/phases/01.1-pc-agent-plugin-architecture-alignment-with-message-bridge-opencode-plugin/01.1-ACCEPTANCE-EVIDENCE.md`
- `.planning/phases/06-reliability-observability-hardening/06-RESEARCH.md`
- `.planning/phases/06-reliability-observability-hardening/06-ACCEPTANCE-EVIDENCE.md`
- `pc-agent-plugin/package.json`
- `pc-agent-plugin/scripts/validate-no-drift.mjs`
- `pc-agent-plugin/src/core/bridge/ProtocolBridge.ts`
- `pc-agent-plugin/src/host-adapter/contracts/HostPluginContract.ts`
- `pc-agent-plugin/src/core/events/PluginEvents.ts`
- `pc-agent-plugin/src/cli/runtime/CliRuntimeBootstrap.ts`
- `pc-agent-plugin/src/host-adapter/HostEventBridge.ts`
- `gateway/src/main/java/com/chatcui/gateway/runtime/ResumeCoordinator.java`
- `gateway/src/main/java/com/chatcui/gateway/runtime/BridgePersistencePublisher.java`
- `gateway/src/test/java/com/chatcui/gateway/runtime/ResumeCoordinatorTest.java`

Context checks:
- `CLAUDE.md`: not present
- `.claude/skills`: not present
- `.agents/skills`: not present

## Metadata

**Confidence breakdown**
- Planning scope and governance constraints: HIGH
- Existing gate/test baseline assessment: HIGH
- Concrete reference-repo delta specifics: MEDIUM (reference repo not present in workspace)

**Research date:** 2026-03-04  
**Valid until:** 2026-03-18 (refresh if reference baseline changes or Phase 07 scope changes)
