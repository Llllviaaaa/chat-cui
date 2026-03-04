import type { OpenCodeMessage } from "../../core/bridge/ProtocolBridge";
import type {
  ReconnectFreshAuthMaterial,
  ResumeAnchor,
  RuntimeHealth
} from "../../core/events/PluginEvents";

export interface HostOutboundEvent {
  type: string;
  payload: {
    contract_version: string;
  } & Record<string, unknown>;
}

export interface HostReconnectPayload {
  reason?: string;
  resume_anchor?: ResumeAnchor;
  fresh_auth?: ReconnectFreshAuthMaterial;
}

export type HostInboundEvent =
  | {
      type: "session.start";
      payload: {
        session_id: string;
        trace_id: string;
      };
    }
  | {
      type: "session.end";
      payload?: {
        reason?: string;
      };
    }
  | {
      type: "opencode.message";
      payload: OpenCodeMessage;
    }
  | {
      type: "gateway.message";
      payload: {
        topic: string;
        data: Record<string, unknown>;
      };
    }
  | {
      type: "runtime.reconnect";
      payload?: HostReconnectPayload;
    }
  | {
      type: "runtime.health";
      payload: {
        health: RuntimeHealth;
        detail?: string;
      };
    }
  | {
      type: "runtime.error";
      payload: {
        code: string;
        message: string;
      };
    }
  | {
      type: string;
      payload?: Record<string, unknown>;
    };

export interface HostPluginOptions {
  emitHostEvent: (event: HostOutboundEvent) => void;
}

export interface HostPluginAdapterContract {
  init(): void;
  start(): void;
  stop(): void;
  dispose(): void;
  onHostEvent(event: HostInboundEvent): void;
  currentState(): string;
}
