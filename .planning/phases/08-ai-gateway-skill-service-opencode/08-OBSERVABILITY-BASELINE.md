# Phase 08 Observability Baseline

This baseline closes `P08-OBS-01` for distributed route/fence/relay/ack/recovery semantics in Phase 08.

## Metric Label Contract

- Allowed metric labels: `component`, `failure_class`, `outcome`, `retryable`
- Canonical `failure_class` values: `auth`, `bridge`, `persistence`, `sendback`, `unknown`
- Forbidden metric labels (high cardinality): `trace_id`, raw `session_id`, raw payload/body, message text
- Correlation fields (`trace_id`, `session_id`, `turn_id`, `seq`, `route_version`) remain in structured logs, not metric tags

## Gateway Metrics (`chatcui.gateway.*`)

| Metric | Type | component | Outcome values | failure_class | retryable |
|---|---|---|---|---|---|
| `chatcui.gateway.bridge.reconnect.outcomes` | Counter | `gateway.bridge.reconnect` | `resumed`, `failed` | `bridge` | `true/false` |
| `chatcui.gateway.bridge.resume.outcomes` | Counter | `gateway.bridge.resume` | `continue`, `dropped_duplicate`, `compensate_gap`, `terminal_failure` | `bridge` | `true/false` |
| `chatcui.gateway.route.outcomes` | Counter | `gateway.route` | `route_conflict`, `owner_fenced`, `terminal_failure` | `bridge` | `false` |
| `chatcui.gateway.relay.outcomes` | Counter | `gateway.relay` | `relay_success`, `relay_timeout` | `bridge` | `false/true` |
| `chatcui.gateway.ack.outcomes` | Counter | `gateway.ack` | `gateway_owner_accepted`, `client_delivered`, `client_delivery_timeout` | `bridge` | `true/false` |
| `chatcui.gateway.persistence.retry.outcomes` | Counter | `gateway.persistence.retry` | `pending`, `saved`, `failed` | `persistence` | `true/false` |
| `chatcui.gateway.persistence.retry.duration` | Timer | `gateway.persistence.retry` | `saved`, `failed` | `persistence` | `true/false` |
| `chatcui.gateway.auth.outcomes` | Counter | `gateway.auth` | `missing_credential`, `invalid_signature`, `timestamp_out_of_window`, `replay_detected`, `cooldown_active`, `credential_disabled`, `permission_denied` | `auth` | `true/false` |

## Skill-Service Metrics (`chatcui.skill.*`)

| Metric | Type | component | Outcome values | failure_class | retryable |
|---|---|---|---|---|---|
| `chatcui.skill.sendback.outcomes` | Counter | `skill-service.sendback` | `success`, `failure`, `dedup` | `sendback` | `true/false` |
| `chatcui.skill.sendback.duration` | Timer | `skill-service.sendback` | `success`, `failure`, `dedup` | `sendback` | `true/false` |
| `chatcui.skill.relay.outcomes` | Counter | `skill-service.relay` | `relay_success`, `relay_timeout`, `owner_fenced`, `replay_window_expired`, `duplicate_dropped` | `bridge` | `false/true` |

## Structured Log Envelope Contract

Phase 08 route/fence/relay/ack/recovery logs must include:

- `stage`
- `trace_id`
- `route_version`
- `session_id`
- `turn_id`
- `seq`
- `topic`
- optional `reason_code`
- optional `next_action`

Payload/body fields are intentionally excluded from Phase 08 runtime logs.

## Outcome Diagnosis Notes

### Route and Fence

- `gateway.route.route_conflict` spike: multiple owners attempted continuation for same session route truth.
- `gateway.route.owner_fenced` spike: stale/fenced owner still processing traffic after migration.

### Relay and Delivery

- `gateway.relay.relay_success` stable growth: first-hop relay path healthy.
- `gateway.relay.relay_timeout` increase: relay publish/dispatch timeout branch active (`RELAY_CLIENT_DELIVERY_TIMEOUT`).
- `gateway.ack.client_delivery_timeout` increase with low relay timeout: includes terminal non-timeout branches (for example `OWNER_FENCED`), inspect `reason_code` in logs.

### Recovery

- `skill-service.relay.replay_window_expired` non-zero: unknown-owner recovery exceeded 15-minute replay window (`ROUTE_REPLAY_WINDOW_EXPIRED`).
- `skill-service.relay.relay_timeout` increase: dispatch pending/retry backlog growing.

## Primary Implementation Artifacts

- `gateway/src/main/java/com/chatcui/gateway/observability/BridgeMetricsRegistry.java`
- `gateway/src/main/java/com/chatcui/gateway/runtime/BridgePersistencePublisher.java`
- `skill-service/src/main/java/com/chatcui/skill/observability/SkillMetricsRecorder.java`
- `skill-service/src/main/java/com/chatcui/skill/relay/RelayEventConsumer.java`
