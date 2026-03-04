# Phase 07 Waiver Template

Use this template only when a `P07-*` hard-gate item cannot pass in the current release candidate.
Default policy is **release-block** unless an approved, time-bounded waiver exists.

## Waiver Record

```yaml
waiver_id: "WVR-P07-<YYYYMMDD>-<NN>"
requirement_id: "P07-<ID>"
gate_id: "GATE-<NAME>"
owner: "<responsible-person-or-role>"
approver: "<approving-person-or-role>"
status: "proposed|approved|expired|closed"
reason: "<why this waiver is needed>"
risk_assessment: "<impact if shipped with waiver>"
mitigation: "<temporary control while waiver is active>"
expiration_utc: "YYYY-MM-DDTHH:mm:ssZ"
rollback_plan: "<how to revert or mitigate if issue worsens>"
closure_action: "<what closes this waiver>"
evidence_link: "<path to acceptance evidence row>"
```

## Required Rules

1. `owner`, `approver`, and `expiration_utc` are mandatory.
2. `requirement_id` must reference one Phase 07 requirement (`P07-*`).
3. Any waiver older than `expiration_utc` is invalid and treated as gate failure.
4. Waiver cannot remove mandatory evidence fields (`command/date/result/session_id/trace_id/evidence_id` where applicable).
5. Waiver must be linked in `07-GAP-CLOSED-CHECKLIST.md` under `waiver_reference`.

## Two-Release Deprecation Guard

If waiver is related to contract deprecation:

1. Mark current release as `deprecation_start`.
2. Keep additive compatibility for the next release window.
3. Allow removal only after a two-release window with documented migration evidence.
