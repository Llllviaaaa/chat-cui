# Phase 2: PC Agent Bridge Core - Research

**Researched:** 2026-03-04  
**Confidence:** HIGH

## Summary

Phase 2 should build on phase 01.1 shared-core runtime and add the missing bridge-core behavior for BRG-01/02/03:

1. Protocol conversion contracts for command/response/error events with deterministic compatibility behavior.
2. Long-connection session runtime with single in-flight turn control and BUSY handling.
3. Adapter integration compatibility (host + CLI) using shared runtime, with lifecycle-safe startup/teardown.
4. Contract/integration evidence and phase closure docs.

## Key Constraints Carried from Context

- Plugin-first architecture is fixed.
- Conversion ownership remains in core bridge layer.
- Stream contract: `delta + final + turn.completed`, strict ordering semantics.
- Mapping failures must be structured and non-sensitive.
- Unknown fields should be preserved in `extensions`.
- Version mismatch should fail explicitly (`VERSION_MISMATCH`).

## Recommended Plan Decomposition

- `02-01`: protocol event schema and conversion contract tests (BRG-03)
- `02-02`: long-lived session runtime and in-flight/session management (BRG-02)
- `02-03`: host/CLI integration alignment with runtime lifecycle and stream path (BRG-01/02)
- `02-04`: acceptance evidence, verification, and phase tracking closure

## Verification Strategy

- Contract tests for protocol mapping:
  - command/request mapping
  - stream delta/final/completed mapping
  - error/unsupported/version mismatch handling
- Runtime integration tests:
  - long-connection start/stop
  - single in-flight turn + BUSY response
  - stream passthrough for one session
- Adapter tests:
  - host lifecycle + stream receive path
  - CLI run-session bridge path uses shared runtime

