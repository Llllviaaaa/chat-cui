import type { GatewayMessage } from "../bridge/ProtocolBridge";

export type GatewayMessageHandler = (message: GatewayMessage) => void;

export interface SessionGatewayTransport {
  connect(): void | Promise<void>;
  disconnect(): void | Promise<void>;
  send(message: GatewayMessage): void | Promise<void>;
  onMessage(handler: GatewayMessageHandler): () => void;
}
