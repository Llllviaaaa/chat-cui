import {
  type GatewayMessage,
  type OpenCodeMessage,
  OPCODE_TYPES,
  type ProtocolBridge
} from "../bridge/ProtocolBridge";
import {
  type PluginEvent,
  type PluginEventHandler,
  type PluginEventName,
  type PluginEventPayloadMap,
  type RuntimeHealth
} from "../events/PluginEvents";
import { LifecycleStateMachine } from "../lifecycle/LifecycleStateMachine";
import type { SessionGatewayTransport } from "./SessionGatewayTransport";

export interface BridgeRuntimeOptions {
  lifecycle?: LifecycleStateMachine;
  bridge: ProtocolBridge;
  transport?: SessionGatewayTransport;
}

export class BridgeRuntime {
  readonly source = "shared-core";
  private reconnectAttempt = 0;
  private readonly lifecycle: LifecycleStateMachine;
  private readonly bridge: ProtocolBridge;
  private readonly transport?: SessionGatewayTransport;
  private readonly handlers: Set<PluginEventHandler> = new Set();
  private unsubscribeTransport?: () => void;
  private activeSessionId?: string;
  private inFlightTurnId?: string;
  private readonly seqByTurn: Map<string, number> = new Map();

  constructor(options: BridgeRuntimeOptions) {
    this.lifecycle = options.lifecycle ?? new LifecycleStateMachine();
    this.bridge = options.bridge;
    this.transport = options.transport;
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
    this.attachTransport();
    this.emit("runtime.started", { state: this.lifecycle.currentState() });
  }

  stop(): void {
    this.lifecycle.stop();
    this.detachTransport();
    this.inFlightTurnId = undefined;
    this.seqByTurn.clear();
    this.emit("runtime.stopped", { state: this.lifecycle.currentState() });
  }

  dispose(): void {
    this.detachTransport();
    this.lifecycle.dispose();
    this.activeSessionId = undefined;
    this.inFlightTurnId = undefined;
    this.seqByTurn.clear();
    this.emit("runtime.disposed", { state: this.lifecycle.currentState() });
  }

  onReconnect(): void {
    this.reconnectAttempt += 1;
    this.lifecycle.onReconnect();
    this.emit("runtime.reconnecting", { attempt: this.reconnectAttempt });
  }

  reportHealth(health: RuntimeHealth, detail?: string): void {
    this.emit("runtime.health", { health, detail });
  }

  reportError(code: string, message: string): void {
    this.emit("runtime.error", { code, message });
  }

  startSession(sessionId: string, traceId: string): void {
    const normalizedSession = sessionId.trim();
    if (!normalizedSession) {
      this.reportError("SESSION_ID_INVALID", "Session id must not be blank.");
      return;
    }

    if (this.activeSessionId && this.activeSessionId !== normalizedSession) {
      this.reportError(
        "SESSION_ACTIVE",
        `Session ${this.activeSessionId} is active. End it before starting ${normalizedSession}.`
      );
      return;
    }

    this.activeSessionId = normalizedSession;
    this.dispatchGateway({
      topic: "skill.session.start",
      data: {
        session_id: normalizedSession,
        trace_id: traceId
      }
    });
  }

  endSession(reason = "normal"): void {
    if (!this.activeSessionId) {
      return;
    }
    this.dispatchGateway({
      topic: "skill.session.end",
      data: {
        session_id: this.activeSessionId,
        reason
      }
    });
    this.activeSessionId = undefined;
    this.inFlightTurnId = undefined;
    this.seqByTurn.clear();
  }

  sendOpenCode(message: OpenCodeMessage): void {
    if (message.type === OPCODE_TYPES.TURN_REQUEST) {
      if (!this.handleTurnRequest(message.payload)) {
        return;
      }
    }

    const mapped = this.bridge.toGateway(message);
    this.dispatchGateway(mapped);
  }

  receiveGateway(topic: string, data: Record<string, unknown>): void {
    this.consumeGatewayMessage({ topic, data });
  }

  currentState(): string {
    return this.lifecycle.currentState();
  }

  private consumeGatewayMessage(message: GatewayMessage): void {
    const converted = this.bridge.fromGateway(message);
    this.handleInboundSequence(converted);
    this.emit("gateway.inbound", {
      topic: converted.type,
      data: converted.payload
    });
  }

  private dispatchGateway(message: GatewayMessage): void {
    this.emit("gateway.outbound", message);
    if (!this.transport) {
      return;
    }

    Promise.resolve(this.transport.send(message)).catch((error: unknown) => {
      this.reportError(
        "GATEWAY_SEND_FAILED",
        error instanceof Error ? error.message : "Gateway send failed."
      );
    });
  }

  private attachTransport(): void {
    if (!this.transport || this.unsubscribeTransport) {
      return;
    }

    this.unsubscribeTransport = this.transport.onMessage((message) => {
      this.consumeGatewayMessage(message);
    });

    Promise.resolve(this.transport.connect()).catch((error: unknown) => {
      this.reportError(
        "GATEWAY_CONNECT_FAILED",
        error instanceof Error ? error.message : "Gateway connect failed."
      );
    });
  }

  private detachTransport(): void {
    if (!this.transport) {
      return;
    }

    this.unsubscribeTransport?.();
    this.unsubscribeTransport = undefined;
    Promise.resolve(this.transport.disconnect()).catch((error: unknown) => {
      this.reportError(
        "GATEWAY_DISCONNECT_FAILED",
        error instanceof Error ? error.message : "Gateway disconnect failed."
      );
    });
  }

  private handleTurnRequest(payload: Record<string, unknown>): boolean {
    const sessionId = getString(payload.session_id);
    const turnId = getString(payload.turn_id);

    if (!sessionId || !turnId) {
      this.reportError(
        "TURN_REQUEST_INVALID",
        "turn.request requires session_id and turn_id."
      );
      return false;
    }

    if (this.activeSessionId && this.activeSessionId !== sessionId) {
      this.reportError(
        "SESSION_MISMATCH",
        `Session mismatch. Active session is ${this.activeSessionId}.`
      );
      return false;
    }

    if (!this.activeSessionId) {
      this.activeSessionId = sessionId;
    }

    if (this.inFlightTurnId && this.inFlightTurnId !== turnId) {
      this.reportError(
        "BUSY",
        `Session ${sessionId} already has in-flight turn ${this.inFlightTurnId}.`
      );
      return false;
    }

    this.inFlightTurnId = turnId;
    return true;
  }

  private handleInboundSequence(message: OpenCodeMessage): void {
    const type = message.type;
    const payload = message.payload;
    const turnId = getString(payload.turn_id);
    const seq = getNumber(payload.seq);

    if (turnId && seq != null) {
      const last = this.seqByTurn.get(turnId);
      if (last != null) {
        if (seq <= last || seq !== last + 1) {
          this.reportError(
            "SEQ_ANOMALY",
            `Turn ${turnId} sequence anomaly: previous=${last}, incoming=${seq}.`
          );
        }
      }
      this.seqByTurn.set(turnId, Math.max(last ?? seq, seq));
    }

    if (type === OPCODE_TYPES.TURN_COMPLETED || type === OPCODE_TYPES.TURN_ERROR) {
      if (turnId && this.inFlightTurnId === turnId) {
        this.inFlightTurnId = undefined;
      }
      if (turnId) {
        this.seqByTurn.delete(turnId);
      }
    }
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

function getString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value : undefined;
}

function getNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}
