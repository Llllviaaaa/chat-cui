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
| `P07-BASE-01` | Baseline package files are published and cross-linked | IN_PROGRESS | phase-07-owner | P07-BASE-01-PENDING | pending://phase-07/P07-BASE-01 | none | Initial package created in plan 07-01; evidence to be finalized in later plans |
| `P07-REF-01` | Reference snapshot record for `message-bridge-opencode-plugin` is captured | OPEN | phase-07-owner | P07-REF-01-PENDING | pending://phase-07/P07-REF-01 | none | Record must include required snapshot fields from runbook |
| `P07-COMPAT-01` | Additive compatibility policy is enforced by verification evidence | OPEN | phase-07-owner | P07-COMPAT-01-PENDING | pending://phase-07/P07-COMPAT-01 | none | No contract-breaking removals permitted |
| `P07-VERSION-01` | Contract version signal coverage is documented for key events | OPEN | phase-07-owner | P07-VERSION-01-PENDING | pending://phase-07/P07-VERSION-01 | none | Key event inventory and version assertions required |
| `P07-GATE-01` | Full hard-gate command set is executable and release-blocking | OPEN | phase-07-owner | P07-GATE-01-PENDING | pending://phase-07/P07-GATE-01 | none | Must cover plugin no-drift, host integration, CLI real-chain, gateway auth/resume |
| `P07-EVID-01` | Evidence schema captures all required audit fields | OPEN | phase-07-owner | P07-EVID-01-PENDING | pending://phase-07/P07-EVID-01 | none | Required fields: command/date/result/session_id/trace_id/evidence_id |
| `P07-GOV-01` | Waiver and deprecation governance workflow is documented | OPEN | phase-07-owner | P07-GOV-01-PENDING | pending://phase-07/P07-GOV-01 | none | Temporary waivers require owner + expiration + closeout action |

## Update Procedure

1. Update `status` and `owner` at the same time when work starts.
2. Fill `evidence_id` and `evidence_link` immediately after each successful gate run.
3. If `status=WAIVED`, add a non-`none` `waiver_reference` and expiration in runbook governance log.
