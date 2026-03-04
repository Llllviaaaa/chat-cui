# Phase 07 Alignment Runbook

## Objective

Provide an auditable operational baseline for phase-07 alignment closure between `pc-agent-plugin` and gateway, using `message-bridge-opencode-plugin` as the reference architecture baseline.

## Scope Boundaries

- `in_scope`: plugin host/runtime/bridge alignment, gateway auth-resume contract alignment, closure governance, and evidence capture.
- `out_of_scope`: web-demo feature expansion, skill-service feature expansion, mobile parity, and unrelated product capability work.

## Reference Baseline Snapshot Method (`P07-REF-01`)

### Required Snapshot Record Fields

| Field | Description |
|-------|-------------|
| `reference_repo` | Canonical repository URL for the reference baseline |
| `reference_tag_or_commit` | Tag or commit hash used as immutable snapshot point |
| `snapshot_date_utc` | Snapshot date/time in UTC (`YYYY-MM-DDTHH:mm:ssZ`) |
| `diff_scope` | Compared surface area (for example: host contracts, runtime events, auth flow, resume policy) |
| `evidence_id` | Stable evidence identifier linked in checklist/mapping artifacts |

### Capture Procedure

1. Resolve baseline source repository for `message-bridge-opencode-plugin`.
2. Capture immutable reference (`reference_tag_or_commit`) and timestamp (`snapshot_date_utc`).
3. Record explicit `diff_scope` for this phase boundary (`pc-agent-plugin` + gateway contracts only).
4. Save the record in the evidence log and reference the generated `evidence_id` in:
   - `07-REQUIREMENT-MAPPING.md`
   - `07-GAP-CLOSED-CHECKLIST.md`
5. Reject closure claims if any required field is missing.

### Snapshot Record Template

```yaml
reference_repo: "<repo-url>"
reference_tag_or_commit: "<tag-or-commit>"
snapshot_date_utc: "YYYY-MM-DDTHH:mm:ssZ"
diff_scope: "pc-agent-plugin + gateway contract alignment"
evidence_id: "P07-REF-01-<timestamp>"
```

## Hard-Gate Inventory (`P07-GATE-01`)

| Gate ID | Coverage | Command | Pass Signal | Evidence Fields |
|---------|----------|---------|-------------|-----------------|
| `GATE-PLUGIN-DRIFT` | Plugin ownership/no-drift | `npm.cmd --prefix pc-agent-plugin run verify:no-drift` | Exit code 0 and no ownership drift findings | command, result, evidence_id |
| `GATE-PLUGIN-HOST` | Host/plugin contract integration | `npm.cmd --prefix pc-agent-plugin run test:plugin-mode-integration` | Contract tests pass with no breaking event contract changes | command, result, evidence_id, trace_id |
| `GATE-PLUGIN-CHAIN` | CLI real-chain path | `npm.cmd --prefix pc-agent-plugin run test:cli-real-chain` | Real-chain integration path reports pass | command, result, evidence_id, session_id |
| `GATE-GATEWAY-AUTH-RESUME` | Gateway auth and resume regressions | `mvn -pl gateway "-Dtest=AuthEntryIntegrationTest,WsAuthFailureIntegrationTest,ResumeCoordinatorTest" test` | Auth/resume regression suite passes | command, result, evidence_id, trace_id |

Release closure is blocked when any required gate is failing unless a time-bounded waiver is approved.

## Compatibility and Version Governance (`P07-COMPAT-01`, `P07-VERSION-01`)

- Backward compatibility is mandatory for host/plugin/gateway contract surfaces by default.
- Contract evolution must be additive-only unless a documented exception is approved.
- Key runtime boundary events must expose an explicit contract-version signal and corresponding verification evidence.
- Deprecation, when unavoidable, requires a two-release window and documented migration notes.

## Evidence Logging Schema (`P07-EVID-01`)

Every alignment claim must be recorded with the following fields:

| Field | Required |
|-------|----------|
| `evidence_id` | yes |
| `requirement_id` | yes |
| `command` | yes |
| `snapshot_date_utc` | yes |
| `result` | yes |
| `session_id` | yes (if flow produces session context) |
| `trace_id` | yes (if flow produces trace context) |
| `owner` | yes |
| `status` | yes |

## Waiver Governance (`P07-GOV-01`)

Waivers are temporary and must be auditable.

Required waiver attributes:

- `waiver_id`
- `requirement_id`
- `owner`
- `status` (`proposed`, `approved`, `expired`, `closed`)
- `reason`
- `expires_at_utc`
- `mitigation`
- `closure_action`

Rules:

1. Waivers cannot remove mandatory evidence recording requirements.
2. Expired waivers automatically return the associated checklist item to `OPEN`.
3. Closure requires either a passing gate result or an approved replacement action with evidence.
