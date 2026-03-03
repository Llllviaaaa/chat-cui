export const AUTH_V1_CODES = [
  "AUTH_V1_MISSING_CREDENTIAL",
  "AUTH_V1_INVALID_SIGNATURE",
  "AUTH_V1_TIMESTAMP_OUT_OF_WINDOW",
  "AUTH_V1_REPLAY_DETECTED",
  "AUTH_V1_COOLDOWN_ACTIVE",
  "AUTH_V1_CREDENTIAL_DISABLED",
  "AUTH_V1_PERMISSION_DENIED"
] as const;

export type AuthFailureCode = (typeof AUTH_V1_CODES)[number];

export interface AuthErrorEnvelope {
  error_code: AuthFailureCode;
  message: string;
  next_action: string;
  retry_after?: number;
  trace_id: string;
  session_id: string;
  debug_id: string;
}

const MESSAGE_MAP: Record<AuthFailureCode, { message: string; nextAction: string; retryAfter?: number }> = {
  AUTH_V1_MISSING_CREDENTIAL: {
    message: "Required authentication metadata is missing.",
    nextAction: "Provide AK/signature/timestamp/nonce/session_id and retry."
  },
  AUTH_V1_INVALID_SIGNATURE: {
    message: "Authentication failed.",
    nextAction: "Verify canonical signing fields and SK, then retry."
  },
  AUTH_V1_TIMESTAMP_OUT_OF_WINDOW: {
    message: "Request timestamp is outside allowed window.",
    nextAction: "Sync client clock and retry with a fresh signature."
  },
  AUTH_V1_REPLAY_DETECTED: {
    message: "Replay request detected.",
    nextAction: "Generate a new nonce and signature, then retry once."
  },
  AUTH_V1_COOLDOWN_ACTIVE: {
    message: "Too many failed authentication attempts.",
    nextAction: "Wait before retrying authentication.",
    retryAfter: 1
  },
  AUTH_V1_CREDENTIAL_DISABLED: {
    message: "Credential is disabled.",
    nextAction: "Contact tenant admin to re-enable or rotate credential."
  },
  AUTH_V1_PERMISSION_DENIED: {
    message: "Access denied for this client.",
    nextAction: "Request required permission for tenant/client binding."
  }
};

export function mapAuthFailure(
  code: AuthFailureCode,
  traceId: string,
  sessionId: string,
  debugId: string,
  retryAfterSeconds?: number
): AuthErrorEnvelope {
  const mapping = MESSAGE_MAP[code];
  return {
    error_code: code,
    message: mapping.message,
    next_action: mapping.nextAction,
    retry_after:
      code === "AUTH_V1_COOLDOWN_ACTIVE"
        ? retryAfterSeconds ?? mapping.retryAfter
        : undefined,
    trace_id: traceId,
    session_id: sessionId,
    debug_id: debugId
  };
}
