# Phase 2 Acceptance Evidence

## Scope

Validate BRG-01, BRG-02, BRG-03 for phase 2:

- Plugin lifecycle compatibility with bridge core
- One-session long-connection stream runtime
- OpenCode <-> internal protocol conversion contracts

## Evidence Log

| Area | Command | Date | Result | Notes |
| --- | --- | --- | --- | --- |
| Contract mapping | `npm.cmd --prefix pc-agent-plugin run test:contracts` | 2026-03-04 | PASS | request/delta/final/completed/error/unsupported/version-mismatch coverage |
| Session runtime | `npm.cmd --prefix pc-agent-plugin run test:bridge-runtime` | 2026-03-04 | PASS | long connection, single in-flight, BUSY, seq anomaly tolerant-continue |
| Host lifecycle | `npm.cmd --prefix pc-agent-plugin run test:host-lifecycle` | 2026-03-04 | PASS | init/start/stop/dispose and reconnect behavior remain stable |
| Host stream contract | `npm.cmd --prefix pc-agent-plugin run test:host-events` | 2026-03-04 | PASS | host session start + turn.request + inbound stream topic mapping |
| CLI bootstrap | `npm.cmd --prefix pc-agent-plugin run test:cli-bootstrap` | 2026-03-04 | PASS | shared runtime factory usage and auth bootstrap compatibility |
| CLI stream path | `npm.cmd --prefix pc-agent-plugin run test:cli-real-chain` | 2026-03-04 | PASS | run-session uses turn.request + session lifecycle markers |
| Phase verification | `npm.cmd --prefix pc-agent-plugin run verify:phase-02` | 2026-03-04 | PASS | no-drift + contracts + runtime + adapters full pipeline |

## Requirement Mapping

- BRG-01: plugin lifecycle and adapter compatibility -> host lifecycle/events + CLI adapter tests.
- BRG-02: long-lived one-session stream runtime -> bridge runtime integration tests.
- BRG-03: bidirectional protocol conversion -> contract tests and stream contract suite.
