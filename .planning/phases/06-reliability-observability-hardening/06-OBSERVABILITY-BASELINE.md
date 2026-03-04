# Phase 06 Observability Baseline

This baseline defines the DEM-02 metrics contract after Phase 06 plans 04-05.

## Metric Label Contract

- Allowed metric labels: `component`, `failure_class`, `outcome`, `retryable`
- Canonical `failure_class` values: `auth`, `bridge`, `persistence`, `sendback`, `unknown`
- Forbidden labels (high cardinality): `trace_id`, raw `session_id`, raw payload content, message text
- Correlation IDs (`trace_id`, `session_id`, `turn_id`, `seq`) remain in structured logs, not metric tags

## Gateway Metrics

| Metric | Type | component | outcome examples | failure_class | retryable |
|---|---|---|---|---|---|
| `chatcui.gateway.bridge.reconnect.outcomes` | Counter | `gateway.bridge.reconnect` | `resumed`, `failed` | `bridge` | `true/false` |
| `chatcui.gateway.bridge.resume.outcomes` | Counter | `gateway.bridge.resume` | `continue`, `dropped_duplicate`, `compensate_gap`, `terminal_failure` | `bridge` | `true/false` |
| `chatcui.gateway.persistence.retry.outcomes` | Counter | `gateway.persistence.retry` | `pending`, `saved`, `failed` | `persistence` | `true/false` |
| `chatcui.gateway.persistence.retry.duration` | Timer | `gateway.persistence.retry` | `saved`, `failed` | `persistence` | `true/false` |

## Skill-Service Metrics

| Metric | Type | component | outcome examples | failure_class | retryable |
|---|---|---|---|---|---|
| `chatcui.skill.sendback.outcomes` | Counter | `skill-service.sendback` | `success`, `failure`, `dedup` | `sendback` | `true/false` |
| `chatcui.skill.sendback.duration` | Timer | `skill-service.sendback` | `success`, `failure`, `dedup` | `sendback` | `true/false` |

## Dashboard Panels

1. **Reconnect / Resume Reliability (bridge)**
- Inputs: `chatcui.gateway.bridge.reconnect.outcomes`, `chatcui.gateway.bridge.resume.outcomes`
- Split by: `component`, `outcome`, `failure_class`, `retryable`
- Goal: detect reconnect degradation and resume anomaly spikes quickly

2. **Persistence Delivery Retry Health (persistence)**
- Inputs: `chatcui.gateway.persistence.retry.outcomes`, `chatcui.gateway.persistence.retry.duration`
- Split by: `outcome`, `retryable`
- Goal: monitor backlog pressure (`pending`) and hard failures (`failed`)

3. **Sendback Delivery Health (sendback)**
- Inputs: `chatcui.skill.sendback.outcomes`, `chatcui.skill.sendback.duration`
- Split by: `outcome`, `retryable`
- Goal: separate new sendback failures from duplicate replay (`dedup`)

4. **Failure-Class Overview**
- Aggregate counters by `failure_class` across gateway + skill-service metrics
- Required classes visible: `auth`, `bridge`, `persistence`, `sendback`, `unknown`

## Default Alert Thresholds

| Alert | Condition | Window | Severity |
|---|---|---|---|
| Bridge reconnect failures | `reconnect.outcomes{outcome=\"failed\"} > 5` | 5m | warning |
| Bridge terminal resume failures | `resume.outcomes{outcome=\"terminal_failure\"} > 0` | 5m | critical |
| Persistence hard failures | `persistence.retry.outcomes{outcome=\"failed\"} > 10` | 10m | warning |
| Persistence latency regression | `p95(persistence.retry.duration{outcome=\"saved\"}) > 2s` | 15m | warning |
| Sendback failure burst | `skill.sendback.outcomes{outcome=\"failure\"} > 5` | 10m | warning |
| Unknown failure class appears | any metric with `failure_class=\"unknown\"` > 0 | 10m | investigate |

## Operator Runbook Notes

1. Start triage at counters grouped by `failure_class`, then drill into `component` and `outcome`.
2. Use `retryable=true` vs `retryable=false` to separate auto-recoverable vs terminal operational actions.
3. For bridge and persistence incidents, correlate with structured logs using shared `trace_id` and tuple fields.
4. Keep dashboard templates strict on allowed labels to prevent cardinality drift during future changes.
