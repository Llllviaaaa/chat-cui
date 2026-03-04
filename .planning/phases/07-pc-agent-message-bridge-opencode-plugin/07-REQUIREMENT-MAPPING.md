# Phase 07 Requirement Mapping

## Purpose

Map every provisional `P07-*` requirement to concrete artifacts, gate commands, and proof signals for phase-07 alignment closure.

## Scope Guardrails

- `in_scope`: `pc-agent-plugin` and gateway contract alignment closure against `message-bridge-opencode-plugin`.
- `out_of_scope`: web-demo feature expansion, skill-service feature expansion, and mobile parity work.

## Gate Command Catalog

- `GATE-PLUGIN-DRIFT`: `npm.cmd --prefix pc-agent-plugin run verify:no-drift`
- `GATE-PLUGIN-HOST`: `npm.cmd --prefix pc-agent-plugin run test:plugin-mode-integration`
- `GATE-PLUGIN-CHAIN`: `npm.cmd --prefix pc-agent-plugin run test:cli-real-chain`
- `GATE-GATEWAY-AUTH-RESUME`: `mvn -pl gateway "-Dtest=AuthEntryIntegrationTest,WsAuthFailureIntegrationTest,ResumeCoordinatorTest" test`

## Requirement Matrix

| Requirement | Alignment Target | Artifacts | Gate Commands | Expected Proof Signals | Evidence Anchor |
|-------------|------------------|-----------|---------------|------------------------|-----------------|
| `P07-BASE-01` | Baseline package completeness | `07-REQUIREMENT-MAPPING.md`, `07-GAP-CLOSED-CHECKLIST.md`, `07-ALIGNMENT-RUNBOOK.md` | `GATE-PLUGIN-DRIFT` | Baseline package files exist with requirement-to-command mapping and governance sections | `evidence_id: P07-BASE-01-PENDING` |
| `P07-REF-01` | Reproducible reference snapshot | `07-ALIGNMENT-RUNBOOK.md` section "Reference Baseline Snapshot Method" | `git clone` + baseline capture workflow from runbook | Snapshot record populated with `reference_repo`, `reference_tag_or_commit`, `snapshot_date_utc`, `diff_scope` | `evidence_id: P07-REF-01-PENDING` |
| `P07-COMPAT-01` | Backward-compatible additive contract policy | `07-ALIGNMENT-RUNBOOK.md` compatibility policy section, closure checklist row | `GATE-PLUGIN-HOST`, `GATE-GATEWAY-AUTH-RESUME` | No contract-breaking removals in aligned interfaces; compatibility policy checklist marked closed | `evidence_id: P07-COMPAT-01-PENDING` |
| `P07-VERSION-01` | Explicit contract-version signal governance | `07-ALIGNMENT-RUNBOOK.md` version signal section, checklist row | `GATE-PLUGIN-HOST` | Key boundary events listed with version-field verification evidence | `evidence_id: P07-VERSION-01-PENDING` |
| `P07-GATE-01` | Hard-gate inventory and release blocking | `07-ALIGNMENT-RUNBOOK.md` gate inventory, checklist row | `GATE-PLUGIN-DRIFT`, `GATE-PLUGIN-HOST`, `GATE-PLUGIN-CHAIN`, `GATE-GATEWAY-AUTH-RESUME` | Gate inventory provides command, expected pass signal, and fail handling | `evidence_id: P07-GATE-01-PENDING` |
| `P07-EVID-01` | Evidence schema standardization | `07-ALIGNMENT-RUNBOOK.md` evidence schema section, checklist row | Any gate command that emits a verifiable result | Evidence records include command/date/result/`session_id`/`trace_id`/`evidence_id` | `evidence_id: P07-EVID-01-PENDING` |
| `P07-GOV-01` | Waiver and deprecation governance | `07-ALIGNMENT-RUNBOOK.md` governance section, checklist row | Governance review step plus gate outcome review | Waiver entries include `owner`, `status`, expiration, and closure action | `evidence_id: P07-GOV-01-PENDING` |

## Cross-Artifact Links

- Checklist source of status truth: `07-GAP-CLOSED-CHECKLIST.md`.
- Operational execution source: `07-ALIGNMENT-RUNBOOK.md`.
- Requirement IDs in this file must match `.planning/ROADMAP.md` and `.planning/REQUIREMENTS.md`.
