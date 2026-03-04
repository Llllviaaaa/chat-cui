# Phase 07 Acceptance Evidence

## Scope

This evidence set closes Phase 07 requirements (`P07-BASE-01` .. `P07-GOV-01`) for plugin+gateway alignment governance.

## Hard-Gate Evidence Log

| Evidence ID | Requirement | Date (UTC) | Command | Result | session_id | trace_id | Notes |
|---|---|---|---|---|---|---|---|
| `EVID-P07-GATE-01-HARDGATE` | `P07-GATE-01` | 2026-03-04T07:57:22Z | `npm.cmd --prefix pc-agent-plugin run verify:phase-07` | PASS | `session-1` | `trace-1` | Includes plugin no-drift, plugin host integration, CLI real-chain, and gateway auth/resume suite. |
| `EVID-P07-COMPAT-01-REGRESSION` | `P07-COMPAT-01` | 2026-03-04T07:47:44Z | `mvn -pl gateway "-Dtest=AuthServiceTest,ErrorResponseFactoryTest,WsAuthHandshakeInterceptorTest,AuthEntryIntegrationTest,WsAuthFailureIntegrationTest,ResumeCoordinatorTest" test` | PASS | `session-2` | `trace-2` | Confirms auth/resume compatibility behavior unchanged while plugin host envelope adds version metadata. |
| `EVID-P07-VERSION-01-HOST` | `P07-VERSION-01` | 2026-03-04T07:47:40Z | `npm.cmd --prefix pc-agent-plugin run test:host-events` | PASS | `session-1` | `trace-1` | Verifies host-facing runtime/gateway events carry additive `contract_version`. |
| `EVID-P07-EVID-01-SCHEMA` | `P07-EVID-01` | 2026-03-04T07:57:22Z | `rg "verify:phase-07|session_id|trace_id|date|result|waiver|EVID-P07-REF-01-BASELINE|reference_repo:|reference_tag_or_commit:|snapshot_date_utc:|diff_scope:" .planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-ACCEPTANCE-EVIDENCE.md .planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-GAP-CLOSED-CHECKLIST.md` | PASS | `session-1` | `trace-1` | Confirms required audit fields are present in evidence/checklist artifacts. |
| `EVID-P07-GOV-01-WAIVER` | `P07-GOV-01` | 2026-03-04T07:57:22Z | `rg "owner|approver|expiration|P07-|two-release|release-block" .planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-WAIVER-TEMPLATE.md .planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-ALIGNMENT-RUNBOOK.md .planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-GAP-CLOSED-CHECKLIST.md` | PASS | `n/a` | `n/a` | Governance template and release-block + two-release policy are documented and linked. |

## Reference Baseline Snapshot Proof (`P07-REF-01`)

evidence_id: EVID-P07-REF-01-BASELINE

```yaml
reference_repo: "NO_REMOTE_CONFIGURED (local repository snapshot used for baseline evidence)"
reference_tag_or_commit: "9efeea6f39b0de9b37790fd6a620b266b3608767"
snapshot_date_utc: "2026-03-04T07:59:06Z"
diff_scope: "pc-agent-plugin + gateway contract alignment (runtime events, auth/resume gate coverage, governance artifacts)"
evidence_id: "EVID-P07-REF-01-BASELINE"
```

Snapshot command record:

| Date (UTC) | Command | Result | Output |
|---|---|---|---|
| 2026-03-04T07:59:06Z | `git remote get-url origin` | PASS (no remote configured) | `error: No such remote 'origin'` |
| 2026-03-04T07:59:06Z | `git rev-parse HEAD` | PASS | `9efeea6f39b0de9b37790fd6a620b266b3608767` |
| 2026-03-04T07:59:06Z | `git show -s --format=%cI HEAD` | PASS | `2026-03-04T15:58:26+08:00` |
| 2026-03-04T07:59:06Z | `git diff --name-only` | PASS | *(empty)* |

## Waiver Status

- Active waivers: none
- Expired waivers: none
- Phase closure mode: release-block without waiver bypass
