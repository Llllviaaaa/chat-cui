import {
  type GatewayMessage,
  type OpenCodeMessage,
  OPCODE_TYPES,
  type ProtocolBridge
} from "../bridge/ProtocolBridge";
import {
  type ReconnectFailureReasonCode,
  type ReconnectFreshAuthMaterial,
  type ReconnectNextAction,
  type PluginEvent,
  type PluginEventHandler,
  type PluginEventName,
  type PluginEventPayloadMap,
  type ResumeAnchor,
  type RuntimeHealth
} from "../events/PluginEvents";
import { LifecycleStateMachine } from "../lifecycle/LifecycleStateMachine";
import type {
  GatewayConnectInput,
  GatewayReconnectInput,
  SessionGatewayTransport
} from "./SessionGatewayTransport";

const COMPENSATION_TOPIC = "skill.turn.compensate";

const DEFAULT_RECONNECT_POLICY: ReconnectPolicy = {
  maxAttempts: 3,
  baseDelayMs: 250,
  maxDelayMs: 2_000
};

export interface BridgeRuntimeOptions {
  lifecycle?: LifecycleStateMachine;
  bridge: ProtocolBridge;
  transport?: SessionGatewayTransport;
  reconnectPolicy?: Partial<ReconnectPolicy>;
  freshAuthFactory?: RuntimeFreshAuthFactory;
  random?: () => number;
  sleep?: (ms: number) => Promise<void>;
}

export interface ReconnectPolicy {
  maxAttempts: number;
  baseDelayMs: number;
  maxDelayMs: number;
}

export interface RuntimeReconnectRequest {
  reason?: string;
  resume_anchor?: ResumeAnchor;
  fresh_auth?: ReconnectFreshAuthMaterial;
}

export interface RuntimeFreshAuthFactoryInput {
  attempt: number;
  session_id: string;
  reason?: string;
}

export type RuntimeFreshAuthFactory = (
  input: RuntimeFreshAuthFactoryInput
) => ReconnectFreshAuthMaterial | Promise<ReconnectFreshAuthMaterial>;

interface ReconnectAttemptContext {
  attempt: number;
  reason?: string;
  resumeAnchor?: ResumeAnchor;
  freshAuth: ReconnectFreshAuthMaterial;
}

interface NormalizedReconnectFailure {
  reasonCode: ReconnectFailureReasonCode;
  nextAction: ReconnectNextAction;
  retryable: boolean;
  detail: string;
}

export class BridgeRuntime {
  readonly source = "shared-core";
  private reconnectAttempt = 0;
  private readonly lifecycle: LifecycleStateMachine;
  private readonly bridge: ProtocolBridge;
  private readonly transport?: SessionGatewayTransport;
  private readonly reconnectPolicy: ReconnectPolicy;
  private readonly freshAuthFactory: RuntimeFreshAuthFactory;
  private readonly random: () => number;
  private readonly sleep: (ms: number) => Promise<void>;
  private readonly handlers: Set<PluginEventHandler> = new Set();
  private unsubscribeTransport?: () => void;
  private activeSessionId?: string;
  private inFlightTurnId?: string;
  private readonly seqByTurn: Map<string, number> = new Map();
  private readonly blockedTurns: Set<string> = new Set();
  private readonly compensationPendingTurns: Set<string> = new Set();
  private reconnectInProgress = false;
  private lastResumeAnchor?: ResumeAnchor;

  constructor(options: BridgeRuntimeOptions) {
    this.lifecycle = options.lifecycle ?? new LifecycleStateMachine();
    this.bridge = options.bridge;
    this.transport = options.transport;
    this.reconnectPolicy = normalizeReconnectPolicy(options.reconnectPolicy);
    this.freshAuthFactory = options.freshAuthFactory ?? defaultFreshAuthFactory;
    this.random = options.random ?? Math.random;
    this.sleep = options.sleep ?? defaultSleep;
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
    this.blockedTurns.clear();
    this.compensationPendingTurns.clear();
    this.lastResumeAnchor = undefined;
    this.emit("runtime.stopped", { state: this.lifecycle.currentState() });
  }

  dispose(): void {
    this.detachTransport();
    this.lifecycle.dispose();
    this.activeSessionId = undefined;
    this.inFlightTurnId = undefined;
    this.seqByTurn.clear();
    this.blockedTurns.clear();
    this.compensationPendingTurns.clear();
    this.lastResumeAnchor = undefined;
    this.emit("runtime.disposed", { state: this.lifecycle.currentState() });
  }

  onReconnect(input: RuntimeReconnectRequest = {}): void {
    if (this.reconnectInProgress) {
      return;
    }

    try {
      this.lifecycle.onReconnect();
    } catch (error) {
      this.reportError(
        "RECONNECT_INVALID_STATE",
        error instanceof Error ? error.message : "Reconnect transition is invalid."
      );
      return;
    }

    const resumeAnchor = this.resolveResumeAnchor(input.resume_anchor);
    const initialAttempt = this.nextReconnectAttempt();

    if (!this.transport) {
      this.emit("runtime.reconnecting", {
        attempt: initialAttempt,
        reason: input.reason,
        resume_anchor: resumeAnchor,
        fresh_auth: input.fresh_auth
      });
      if (resumeAnchor) {
        this.emit("runtime.resumed", {
          attempt: initialAttempt,
          resume_anchor: resumeAnchor
        });
      }
      return;
    }

    this.reconnectInProgress = true;
    void this.executeReconnectFlow({
      input,
      initialAttempt,
      resumeAnchor
    }).finally(() => {
      this.reconnectInProgress = false;
    });
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
    this.blockedTurns.clear();
    this.compensationPendingTurns.clear();
    this.lastResumeAnchor = undefined;
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
    if (!this.handleInboundSequence(converted)) {
      return;
    }
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
    const seq = getNumber(payload.seq);

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
    if (seq != null) {
      this.seqByTurn.set(turnId, seq);
      this.lastResumeAnchor = {
        session_id: sessionId,
        turn_id: turnId,
        seq
      };
    }
    return true;
  }

  private handleInboundSequence(message: OpenCodeMessage): boolean {
    const type = message.type;
    const payload = message.payload;
    const sessionId = getString(payload.session_id);
    const turnId = getString(payload.turn_id);
    const seq = getNumber(payload.seq);
    const traceId = getString(payload.trace_id);

    if (turnId && seq != null) {
      const last = this.seqByTurn.get(turnId);
      const expectedSeq = (last ?? 0) + 1;

      if (last != null) {
        if (seq <= last) {
          this.reportError(
            "SEQ_DUPLICATE_DROPPED",
            `Turn ${turnId} duplicate sequence dropped: previous=${last}, incoming=${seq}.`
          );
          return false;
        }

        if (seq > last + 1) {
          this.reportError(
            "SEQ_GAP_COMPENSATION_REQUIRED",
            `Turn ${turnId} sequence gap detected: expected=${last + 1}, incoming=${seq}.`
          );
          this.triggerCompensationSignal({
            sessionId,
            turnId,
            traceId,
            expectedSeq: last + 1,
            incomingSeq: seq
          });
          return false;
        }
      }

      if (this.blockedTurns.has(turnId)) {
        if (seq !== expectedSeq) {
          return false;
        }
        this.blockedTurns.delete(turnId);
        this.compensationPendingTurns.delete(turnId);
      }

      this.seqByTurn.set(turnId, seq);
      if (sessionId) {
        this.lastResumeAnchor = {
          session_id: sessionId,
          turn_id: turnId,
          seq
        };
      }
    }

    if (type === OPCODE_TYPES.TURN_COMPLETED || type === OPCODE_TYPES.TURN_ERROR) {
      if (turnId && this.inFlightTurnId === turnId) {
        this.inFlightTurnId = undefined;
      }
      if (turnId) {
        this.seqByTurn.delete(turnId);
        this.blockedTurns.delete(turnId);
        this.compensationPendingTurns.delete(turnId);
      }
    }
    return true;
  }

  private async executeReconnectFlow(params: {
    input: RuntimeReconnectRequest;
    initialAttempt: number;
    resumeAnchor?: ResumeAnchor;
  }): Promise<void> {
    let currentAttempt = params.initialAttempt;

    for (let flowAttempt = 1; flowAttempt <= this.reconnectPolicy.maxAttempts; flowAttempt += 1) {
      const resumeAnchor =
        flowAttempt === 1 ? params.resumeAnchor : this.resolveResumeAnchor(params.input.resume_anchor);
      const freshAuth = await this.buildFreshAuth({
        attempt: currentAttempt,
        reason: params.input.reason,
        resumeAnchor,
        initialAuth: flowAttempt === 1 ? params.input.fresh_auth : undefined
      });

      this.emit("runtime.reconnecting", {
        attempt: currentAttempt,
        reason: params.input.reason,
        resume_anchor: resumeAnchor,
        fresh_auth: freshAuth
      });

      try {
        await this.performReconnectAttempt({
          attempt: currentAttempt,
          reason: params.input.reason,
          resumeAnchor,
          freshAuth
        });
        if (resumeAnchor) {
          this.lastResumeAnchor = resumeAnchor;
          this.emit("runtime.resumed", {
            attempt: currentAttempt,
            resume_anchor: resumeAnchor
          });
        }
        return;
      } catch (error) {
        const normalized = normalizeReconnectFailure(error);
        const isTerminal = flowAttempt >= this.reconnectPolicy.maxAttempts;
        if (isTerminal) {
          const terminal = toTerminalReconnectFailure(normalized);
          this.emit("runtime.failed", {
            attempt: currentAttempt,
            reason_code: terminal.reasonCode,
            retryable: false,
            next_action: terminal.nextAction,
            detail: terminal.detail
          });
          return;
        }

        await this.waitBeforeRetry(flowAttempt);
        currentAttempt = this.nextReconnectAttempt();
      }
    }
  }

  private async performReconnectAttempt(
    context: ReconnectAttemptContext
  ): Promise<void> {
    if (!this.transport) {
      return;
    }

    const connectInput: GatewayConnectInput = {
      fresh_auth: context.freshAuth
    };
    const reconnectInput: GatewayReconnectInput | undefined = context.resumeAnchor
      ? {
          attempt: context.attempt,
          reason: context.reason,
          resume_anchor: context.resumeAnchor,
          fresh_auth: context.freshAuth
        }
      : undefined;

    if (this.transport.reconnect && reconnectInput) {
      await Promise.resolve(this.transport.reconnect(reconnectInput)).catch((error: unknown) => {
        throw new RuntimeReconnectFailure(
          "TRANSPORT_RECONNECT_FAILED",
          "retry_automatically",
          true,
          toErrorMessage(error, "Gateway reconnect failed.")
        );
      });
    } else {
      await Promise.resolve(this.transport.connect(connectInput)).catch((error: unknown) => {
        throw new RuntimeReconnectFailure(
          "TRANSPORT_RECONNECT_FAILED",
          "retry_automatically",
          true,
          toErrorMessage(error, "Gateway connect failed during reconnect.")
        );
      });
    }

    if (this.transport.resume) {
      if (!context.resumeAnchor) {
        throw new RuntimeReconnectFailure(
          "RESUME_ANCHOR_REJECTED",
          "restart_session",
          false,
          "Reconnect resume anchor is missing."
        );
      }
      await Promise.resolve(this.transport.resume(context.resumeAnchor)).catch(
        (error: unknown) => {
          throw new RuntimeReconnectFailure(
            "RESUME_ANCHOR_REJECTED",
            "restart_session",
            true,
            toErrorMessage(error, "Gateway resume rejected.")
          );
        }
      );
    }
  }

  private async buildFreshAuth(params: {
    attempt: number;
    reason?: string;
    resumeAnchor?: ResumeAnchor;
    initialAuth?: ReconnectFreshAuthMaterial;
  }): Promise<ReconnectFreshAuthMaterial> {
    if (params.initialAuth) {
      return params.initialAuth;
    }

    const sessionId =
      params.resumeAnchor?.session_id ??
      this.activeSessionId ??
      this.lastResumeAnchor?.session_id;

    if (!sessionId) {
      throw new RuntimeReconnectFailure(
        "AUTH_REFRESH_FAILED",
        "reauthenticate_and_retry",
        false,
        "Unable to generate fresh auth without an active session."
      );
    }

    return this.freshAuthFactory({
      attempt: params.attempt,
      session_id: sessionId,
      reason: params.reason
    });
  }

  private resolveResumeAnchor(explicit?: ResumeAnchor): ResumeAnchor | undefined {
    if (explicit) {
      return explicit;
    }
    if (this.lastResumeAnchor) {
      return this.lastResumeAnchor;
    }
    if (!this.activeSessionId || !this.inFlightTurnId) {
      return undefined;
    }
    return {
      session_id: this.activeSessionId,
      turn_id: this.inFlightTurnId,
      seq: this.seqByTurn.get(this.inFlightTurnId) ?? 0
    };
  }

  private nextReconnectAttempt(): number {
    this.reconnectAttempt += 1;
    return this.reconnectAttempt;
  }

  private async waitBeforeRetry(flowAttempt: number): Promise<void> {
    const maxDelay = Math.min(
      this.reconnectPolicy.maxDelayMs,
      this.reconnectPolicy.baseDelayMs * 2 ** (flowAttempt - 1)
    );
    const delay = Math.floor(this.random() * Math.max(1, maxDelay + 1));
    if (delay > 0) {
      await this.sleep(delay);
    }
  }

  private triggerCompensationSignal(params: {
    sessionId?: string;
    turnId: string;
    traceId?: string;
    expectedSeq: number;
    incomingSeq: number;
  }): void {
    this.blockedTurns.add(params.turnId);
    if (this.compensationPendingTurns.has(params.turnId)) {
      return;
    }
    this.compensationPendingTurns.add(params.turnId);
    this.dispatchGateway({
      topic: COMPENSATION_TOPIC,
      data: {
        session_id: params.sessionId ?? this.activeSessionId,
        turn_id: params.turnId,
        expected_seq: params.expectedSeq,
        incoming_seq: params.incomingSeq,
        trace_id: params.traceId,
        action: "compensate_and_resume"
      }
    });
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

function normalizeReconnectPolicy(
  policy?: Partial<ReconnectPolicy>
): ReconnectPolicy {
  return {
    maxAttempts: Math.max(1, policy?.maxAttempts ?? DEFAULT_RECONNECT_POLICY.maxAttempts),
    baseDelayMs: Math.max(0, policy?.baseDelayMs ?? DEFAULT_RECONNECT_POLICY.baseDelayMs),
    maxDelayMs: Math.max(
      policy?.baseDelayMs ?? DEFAULT_RECONNECT_POLICY.baseDelayMs,
      policy?.maxDelayMs ?? DEFAULT_RECONNECT_POLICY.maxDelayMs
    )
  };
}

function defaultFreshAuthFactory(
  input: RuntimeFreshAuthFactoryInput
): ReconnectFreshAuthMaterial {
  const timestamp = Math.floor(Date.now() / 1000);
  return {
    ak: "runtime-reconnect",
    tenant_id: "runtime-reconnect",
    client_id: "runtime-reconnect",
    session_id: input.session_id,
    timestamp,
    nonce: `reconnect-${input.attempt}-${timestamp}`,
    signature: `signature-${input.attempt}-${timestamp}`
  };
}

async function defaultSleep(ms: number): Promise<void> {
  await new Promise<void>((resolve) => {
    setTimeout(resolve, ms);
  });
}

function normalizeReconnectFailure(error: unknown): NormalizedReconnectFailure {
  if (error instanceof RuntimeReconnectFailure) {
    return {
      reasonCode: error.reasonCode,
      nextAction: error.nextAction,
      retryable: error.retryable,
      detail: error.message
    };
  }
  return {
    reasonCode: "TRANSPORT_RECONNECT_FAILED",
    nextAction: "retry_automatically",
    retryable: true,
    detail: toErrorMessage(error, "Reconnect failed.")
  };
}

function toTerminalReconnectFailure(
  failure: NormalizedReconnectFailure
): NormalizedReconnectFailure {
  if (failure.reasonCode === "AUTH_REFRESH_FAILED") {
    return {
      reasonCode: "AUTH_REFRESH_FAILED",
      nextAction: "reauthenticate_and_retry",
      retryable: false,
      detail: failure.detail
    };
  }
  if (failure.reasonCode === "RESUME_ANCHOR_REJECTED") {
    return {
      reasonCode: "RESUME_ANCHOR_REJECTED",
      nextAction: "restart_session",
      retryable: false,
      detail: failure.detail
    };
  }
  return {
    reasonCode: "RETRY_BUDGET_EXHAUSTED",
    nextAction: "restart_session",
    retryable: false,
    detail: failure.detail
  };
}

function toErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

class RuntimeReconnectFailure extends Error {
  constructor(
    readonly reasonCode: ReconnectFailureReasonCode,
    readonly nextAction: ReconnectNextAction,
    readonly retryable: boolean,
    message: string
  ) {
    super(message);
  }
}
