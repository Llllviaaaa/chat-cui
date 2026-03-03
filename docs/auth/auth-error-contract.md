# Authentication Error Contract (AUTH_V1)

## 1. Envelope

All authentication rejections return this deterministic payload shape:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `error_code` | string | yes | Versioned public code (`AUTH_V1_*`) |
| `message` | string | yes | Safe user-facing message, no secrets |
| `next_action` | string | yes | Recovery instruction for client/UI |
| `retry_after` | number | no | Seconds until next valid attempt |
| `trace_id` | string | yes | Request correlation id |
| `session_id` | string | yes | Session correlation id |
| `debug_id` | string | yes | Internal support correlation id |

## 2. Code Registry

| Error Code | Failure Class | Trigger |
| --- | --- | --- |
| `AUTH_V1_MISSING_CREDENTIAL` | Input validation | AK/signature/timestamp/nonce/session_id missing |
| `AUTH_V1_INVALID_SIGNATURE` | Signature validation | Canonical signature mismatch |
| `AUTH_V1_TIMESTAMP_OUT_OF_WINDOW` | Time validation | Timestamp outside skew window |
| `AUTH_V1_REPLAY_DETECTED` | Replay protection | Nonce already used in replay TTL |
| `AUTH_V1_COOLDOWN_ACTIVE` | Abuse protection | Progressive cooldown still active |
| `AUTH_V1_CREDENTIAL_DISABLED` | Credential lifecycle | Credential state is `Disabled` |
| `AUTH_V1_PERMISSION_DENIED` | Authorization | Credential authenticated but not allowed |

## 3. HTTP and WebSocket Mapping

| Error Code | HTTP Status | WS Close Code | Message | Next Action | Retry After |
| --- | --- | --- | --- | --- | --- |
| `AUTH_V1_MISSING_CREDENTIAL` | 400 | 4400 | Required authentication metadata is missing. | Provide AK/signature/timestamp/nonce/session_id and retry. | no |
| `AUTH_V1_INVALID_SIGNATURE` | 401 | 4401 | Authentication failed. | Verify canonical signing fields and SK, then retry. | no |
| `AUTH_V1_TIMESTAMP_OUT_OF_WINDOW` | 401 | 4401 | Request timestamp is outside allowed window. | Sync client clock and retry with a fresh signature. | no |
| `AUTH_V1_REPLAY_DETECTED` | 401 | 4401 | Replay request detected. | Generate a new nonce and signature, then retry once. | no |
| `AUTH_V1_COOLDOWN_ACTIVE` | 429 | 4429 | Too many failed authentication attempts. | Wait before retrying authentication. | yes |
| `AUTH_V1_CREDENTIAL_DISABLED` | 403 | 4403 | Credential is disabled. | Contact tenant admin to re-enable or rotate credential. | no |
| `AUTH_V1_PERMISSION_DENIED` | 403 | 4403 | Access denied for this client. | Request required permission for tenant/client binding. | no |

Notes:

- WS behavior: close during auth handshake with code + payload including the same `error_code` and envelope fields.
- `retry_after` is only present for `AUTH_V1_COOLDOWN_ACTIVE`.

## 4. Redaction and Safety

- Public `message` must remain non-sensitive and deterministic.
- Payload and logs must never include raw signature, SK material, or unmasked AK.
- `trace_id` + `session_id` + `debug_id` are mandatory in every auth rejection.

## 5. Contract Drift Guard

- `gateway` test suite parses this document and validates `AUTH_V1_*` code parity with `AuthFailureCode`.
- Any code added/removed here must be reflected in enum and mapping tests in the same change.
