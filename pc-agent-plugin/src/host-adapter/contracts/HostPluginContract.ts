import type { OpenCodeMessage } from "../../core/bridge/ProtocolBridge";
import type { RuntimeHealth } from "../../core/events/PluginEvents";

export interface HostOutboundEvent {
  type: string;
  payload: Record<string, unknown>;
}

export type HostInboundEvent =
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
      payload?: {
        reason?: string;
      };
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
