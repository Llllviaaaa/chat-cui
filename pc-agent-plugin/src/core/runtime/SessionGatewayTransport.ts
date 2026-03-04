import type { GatewayMessage } from "../bridge/ProtocolBridge";
import type {
  ReconnectFreshAuthMaterial,
  ResumeAnchor
} from "../events/PluginEvents";

export type GatewayMessageHandler = (message: GatewayMessage) => void;

export interface GatewayConnectInput {
  fresh_auth?: ReconnectFreshAuthMaterial;
}

export interface GatewayReconnectInput {
  attempt: number;
  reason?: string;
  resume_anchor: ResumeAnchor;
  fresh_auth: ReconnectFreshAuthMaterial;
}

export interface SessionGatewayTransport {
  connect(input?: GatewayConnectInput): void | Promise<void>;
  reconnect?(input: GatewayReconnectInput): void | Promise<void>;
  resume?(anchor: ResumeAnchor): void | Promise<void>;
  disconnect(): void | Promise<void>;
  send(message: GatewayMessage): void | Promise<void>;
  onMessage(handler: GatewayMessageHandler): () => void;
}
