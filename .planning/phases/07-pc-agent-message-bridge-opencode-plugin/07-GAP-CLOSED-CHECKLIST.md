# Phase 07 Gap Closed Checklist

## Checklist Semantics

- `status`: `OPEN` | `IN_PROGRESS` | `CLOSED` | `WAIVED`
- `owner`: accountable role or person for closure evidence
- `evidence_link`: path or URL to supporting evidence
- `evidence_id`: stable identifier used in requirement mapping and runbook logs
- `waiver_reference`: waiver record ID (or `none`)

## Alignment Closure Items

| Requirement | Alignment Item | status | owner | evidence_id | evidence_link | waiver_reference | Notes |
|-------------|----------------|--------|-------|-------------|---------------|------------------|-------|
| `P07-BASE-01` | Baseline package files are published and cross-linked | CLOSED | phase-07-owner | EVID-P07-BASE-01-BASELINE | .planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-01-SUMMARY.md | none | Completed in plan 07-01 with mapping/checklist/runbook artifacts |
| `P07-REF-01` | Reference snapshot protocol for `message-bridge-opencode-plugin` is defined | CLOSED | phase-07-owner | EVID-P07-REF-01-BASELINE | .planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-ACCEPTANCE-EVIDENCE.md | none | Baseline proof fields captured with citation chain into verification report |
| `P07-COMPAT-01` | Additive compatibility policy is enforced by verification evidence | CLOSED | phase-07-owner | EVID-P07-COMPAT-01-REGRESSION | .planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-02-SUMMARY.md | none | No contract-breaking removals; gateway regression suites green |
| `P07-VERSION-01` | Contract version signal coverage is documented for key events | CLOSED | phase-07-owner | EVID-P07-VERSION-01-HOST | .planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-02-SUMMARY.md | none | Host-facing key runtime/gateway events include `contract_version` |
| `P07-GATE-01` | Full hard-gate command set is executable and release-blocking | CLOSED | phase-07-owner | EVID-P07-GATE-01-HARDGATE | .planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-ACCEPTANCE-EVIDENCE.md | none | `verify:phase-07` passed locally and is enforced in CI |
| `P07-EVID-01` | Evidence schema captures all required audit fields | CLOSED | phase-07-owner | EVID-P07-EVID-01-SCHEMA | .planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-ACCEPTANCE-EVIDENCE.md | none | Evidence includes command/date/result/session_id/trace_id/evidence_id |
| `P07-GOV-01` | Waiver and deprecation governance workflow is documented | CLOSED | phase-07-owner | EVID-P07-GOV-01-WAIVER | .planning/phases/07-pc-agent-message-bridge-opencode-plugin/07-WAIVER-TEMPLATE.md | none | Waiver policy enforces owner+approver+expiration and two-release deprecation window |

## Update Procedure

1. Update `status` and `owner` at the same time when work starts.
2. Fill `evidence_id` and `evidence_link` immediately after each successful gate run.
3. If `status=WAIVED`, add a non-`none` `waiver_reference` and expiration in runbook governance log.

## Governance Linkage

- Default policy is `release-block`: OPEN items cannot ship without pass evidence or approved waiver.
- Waiver records must use `07-WAIVER-TEMPLATE.md` and include `owner`, `approver`, and `expiration_utc`.
