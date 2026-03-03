# Auth Foundation Acceptance Matrix

## Scope

Phase 1 acceptance evidence for:

- `AUT-01`: Client runtime can configure AK/SK credentials for gateway authentication.
- `AUT-02`: AI-Gateway validates AK/SK before long-lived connection establishment.
- `AUT-03`: Invalid or missing AK/SK returns explicit failure and blocks session start.

## Requirement to Evidence

| Requirement | Test Class | Core Scenarios | Expected AUTH_V1 Outputs |
|---|---|---|---|
| AUT-01 | `com.chatcui.agent.config.AuthConfigLoaderTest` | valid load, missing field reject, disabled block, rotating warn, redaction | N/A (client bootstrap) |
| AUT-01 | `com.chatcui.agent.auth.WindowsKeystoreCredentialProviderTest` | secret read/update, not found typed error, no secret leakage | N/A (client bootstrap) |
| AUT-01 + AUT-02 + AUT-03 | `com.chatcui.agent.integration.ReauthOnReconnectIntegrationTest` | reconnect without fresh auth rejected, fresh reconnect accepted, disabled credential blocked | `AUTH_V1_REPLAY_DETECTED`, `AUTH_V1_CREDENTIAL_DISABLED` |
| AUT-02 + AUT-03 | `com.chatcui.gateway.auth.AuthServiceTest` | missing, invalid signature, replay, skew, cooldown, disabled, success | `AUTH_V1_MISSING_CREDENTIAL`, `AUTH_V1_INVALID_SIGNATURE`, `AUTH_V1_REPLAY_DETECTED`, `AUTH_V1_TIMESTAMP_OUT_OF_WINDOW`, `AUTH_V1_COOLDOWN_ACTIVE`, `AUTH_V1_CREDENTIAL_DISABLED` |
| AUT-02 + AUT-03 | `com.chatcui.gateway.auth.ErrorResponseFactoryTest` | HTTP/WS mapping, required fields, retry_after policy | `AUTH_V1_*` mapping table validation |
| AUT-02 + AUT-03 | `com.chatcui.gateway.ws.WsAuthHandshakeInterceptorTest` | invalid signature rejection, valid acceptance | `AUTH_V1_INVALID_SIGNATURE` |
| AUT-02 + AUT-03 | `com.chatcui.gateway.integration.AuthEntryIntegrationTest` | valid entry, missing metadata, replay, cooldown | `AUTH_V1_MISSING_CREDENTIAL`, `AUTH_V1_REPLAY_DETECTED`, `AUTH_V1_COOLDOWN_ACTIVE` |
| AUT-02 + AUT-03 | `com.chatcui.gateway.integration.WsAuthFailureIntegrationTest` | invalid signature close mapping, permission denied close mapping | `AUTH_V1_INVALID_SIGNATURE`, `AUTH_V1_PERMISSION_DENIED` |

## Failure Contract Checklist

- [x] `error_code` is present in all auth rejections.
- [x] `message` is safe for user display (no raw SK/signature).
- [x] `next_action` is present with deterministic guidance.
- [x] `trace_id`, `session_id`, `debug_id` are returned for diagnostics.
- [x] `retry_after` appears only for cooldown scenarios.
- [x] HTTP and WS mappings share the same `AUTH_V1_*` codes.

## Sign-off Criteria

- [x] All listed tests pass in CI/local execution.
- [x] Each AUT requirement has executable evidence.
- [x] No alternate/undocumented auth failure schema exists in phase 1 artifacts.
