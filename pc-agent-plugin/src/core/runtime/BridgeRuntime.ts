import {
  type OpenCodeMessage,
  type ProtocolBridge
} from "../bridge/ProtocolBridge";
import {
  type PluginEvent,
  type PluginEventHandler,
  type PluginEventName,
  type PluginEventPayloadMap
} from "../events/PluginEvents";
import { LifecycleStateMachine } from "../lifecycle/LifecycleStateMachine";

export interface BridgeRuntimeOptions {
  lifecycle?: LifecycleStateMachine;
  bridge: ProtocolBridge;
}

export class BridgeRuntime {
  readonly source = "shared-core";
  private reconnectAttempt = 0;
  private readonly lifecycle: LifecycleStateMachine;
  private readonly bridge: ProtocolBridge;
  private readonly handlers: Set<PluginEventHandler> = new Set();

  constructor(options: BridgeRuntimeOptions) {
    this.lifecycle = options.lifecycle ?? new LifecycleStateMachine();
    this.bridge = options.bridge;
  }

  subscribe(handler: PluginEventHandler): () => void {
    this.handlers.add(handler);
    return () => this.handlers.delete(handler);
  }

  init(): void {
    this.lifecycle.init();
  }

  start(): void {
    this.lifecycle.start();
    this.emit("runtime.started", { state: this.lifecycle.currentState() });
  }

  stop(): void {
    this.lifecycle.stop();
    this.emit("runtime.stopped", { state: this.lifecycle.currentState() });
  }

  dispose(): void {
    this.lifecycle.dispose();
    this.emit("runtime.disposed", { state: this.lifecycle.currentState() });
  }

  onReconnect(): void {
    this.reconnectAttempt += 1;
    this.lifecycle.onReconnect();
    this.emit("runtime.reconnecting", { attempt: this.reconnectAttempt });
  }

  sendOpenCode(message: OpenCodeMessage): void {
    const mapped = this.bridge.toGateway(message);
    this.emit("gateway.outbound", mapped);
  }

  receiveGateway(topic: string, data: Record<string, unknown>): void {
    const message = this.bridge.fromGateway({ topic, data });
    this.emit("gateway.inbound", {
      topic: message.type,
      data: message.payload
    });
  }

  currentState(): string {
    return this.lifecycle.currentState();
  }

  private emit<K extends PluginEventName>(
    name: K,
    payload: PluginEventPayloadMap[K]
  ): void {
    const event: PluginEvent<K> = { name, payload };
    for (const handler of this.handlers) {
      handler(event);
    }
  }
}
