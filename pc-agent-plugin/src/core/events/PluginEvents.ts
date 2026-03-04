export type RuntimeHealth = "healthy" | "degraded" | "down";

export const HOST_EVENT_CONTRACT_VERSION = "1.0.0";

export const FAILURE_CLASS_VALUES = [
  "auth",
  "bridge",
  "persistence",
  "sendback",
  "unknown"
] as const;

export type FailureClass = (typeof FAILURE_CLASS_VALUES)[number];

export const FAILURE_CLASS_RETRYABLE_DEFAULT: Record<FailureClass, boolean> = {
  auth: false,
  bridge: true,
  persistence: true,
  sendback: true,
  unknown: false
};

export const FAILURE_ENVELOPE_REQUIRED_FIELDS = [
  "tenant_id",
  "client_id",
  "session_id",
  "turn_id",
  "seq",
  "trace_id",
  "error_code",
  "component",
  "status",
  "failure_class",
  "retryable"
] as const;

export type FailureEnvelopeRequiredField =
  (typeof FAILURE_ENVELOPE_REQUIRED_FIELDS)[number];

export interface FailureEnvelope {
  tenant_id: string;
  client_id: string;
  session_id: string;
  turn_id: string;
  seq: number;
  trace_id: string;
  error_code: string;
  component: string;
  status: string;
  failure_class: FailureClass;
  retryable: boolean;
}

export interface ResumeAnchor {
  session_id: string;
  turn_id: string;
  seq: number;
}

export interface ReconnectFreshAuthMaterial {
  ak: string;
  tenant_id: string;
  client_id: string;
  session_id: string;
  timestamp: number;
  nonce: string;
  signature: string;
}

export const RECONNECT_FAILURE_REASON_CODES = [
  "AUTH_REFRESH_FAILED",
  "TRANSPORT_RECONNECT_FAILED",
  "RESUME_ANCHOR_REJECTED",
  "RETRY_BUDGET_EXHAUSTED",
  "SESSION_TERMINATED"
] as const;

export type ReconnectFailureReasonCode =
  (typeof RECONNECT_FAILURE_REASON_CODES)[number];

export const RECONNECT_NEXT_ACTIONS = [
  "retry_automatically",
  "reauthenticate_and_retry",
  "restart_session",
  "contact_support"
] as const;

export type ReconnectNextAction = (typeof RECONNECT_NEXT_ACTIONS)[number];

export interface RuntimeReconnectingPayload {
  attempt: number;
  reason?: string;
  resume_anchor?: ResumeAnchor;
  fresh_auth?: ReconnectFreshAuthMaterial;
}

export interface RuntimeResumedPayload {
  attempt: number;
  resume_anchor: ResumeAnchor;
}

export interface RuntimeFailedPayload {
  attempt: number;
  reason_code: ReconnectFailureReasonCode;
  retryable: boolean;
  next_action: ReconnectNextAction;
  detail?: string;
}

export type PluginEventName =
  | "runtime.started"
  | "runtime.stopped"
  | "runtime.disposed"
  | "runtime.reconnecting"
  | "runtime.resumed"
  | "runtime.failed"
  | "runtime.health"
  | "runtime.error"
  | "gateway.outbound"
  | "gateway.inbound";

export interface PluginEventPayloadMap {
  "runtime.started": { state: string };
  "runtime.stopped": { state: string };
  "runtime.disposed": { state: string };
  "runtime.reconnecting": RuntimeReconnectingPayload;
  "runtime.resumed": RuntimeResumedPayload;
  "runtime.failed": RuntimeFailedPayload;
  "runtime.health": { health: RuntimeHealth; detail?: string };
  "runtime.error": { code: string; message: string };
  "gateway.outbound": { topic: string; data: Record<string, unknown> };
  "gateway.inbound": { topic: string; data: Record<string, unknown> };
}

export type PluginEvent<K extends PluginEventName = PluginEventName> = {
  name: K;
  payload: PluginEventPayloadMap[K];
};

export type PluginEventHandler<K extends PluginEventName = PluginEventName> = (
  event: PluginEvent<K>
) => void;
