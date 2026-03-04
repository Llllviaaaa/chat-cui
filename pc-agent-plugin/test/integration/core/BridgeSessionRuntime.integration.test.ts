import { describe, expect, it } from "vitest";
import {
  BRIDGE_PROTOCOL_VERSION,
  GATEWAY_TOPICS,
  OPCODE_TYPES,
  type GatewayMessage
} from "../../../src/core/bridge/ProtocolBridge";
import type { ResumeAnchor } from "../../../src/core/events/PluginEvents";
import { createBridgeRuntime } from "../../../src/core/runtime/BridgeRuntimeFactory";
import type {
  GatewayConnectInput,
  GatewayReconnectInput,
  SessionGatewayTransport
} from "../../../src/core/runtime/SessionGatewayTransport";

class MemoryGatewayTransport implements SessionGatewayTransport {
  readonly sent: GatewayMessage[] = [];
  readonly receivedHandlers: Array<(message: GatewayMessage) => void> = [];
  readonly connectInputs: Array<GatewayConnectInput | undefined> = [];
  readonly reconnectInputs: GatewayReconnectInput[] = [];
  readonly resumeAnchors: ResumeAnchor[] = [];
  connectCalls = 0;
  disconnectCalls = 0;
  reconnectFailuresRemaining = 0;
  resumeFailuresRemaining = 0;
  reconnectBlocker?: Promise<void>;

  connect(input?: GatewayConnectInput): void {
    this.connectCalls += 1;
    this.connectInputs.push(input);
  }

  reconnect(input: GatewayReconnectInput): void | Promise<void> {
    this.reconnectInputs.push(input);
    if (this.reconnectFailuresRemaining > 0) {
      this.reconnectFailuresRemaining -= 1;
      throw new Error("transport reconnect failure");
    }
    if (this.reconnectBlocker) {
      return this.reconnectBlocker;
    }
  }

  resume(anchor: ResumeAnchor): void {
    this.resumeAnchors.push(anchor);
    if (this.resumeFailuresRemaining > 0) {
      this.resumeFailuresRemaining -= 1;
      throw new Error("transport resume failure");
    }
  }

  disconnect(): void {
    this.disconnectCalls += 1;
  }

  send(message: GatewayMessage): void {
    this.sent.push(message);
  }

  onMessage(handler: (message: GatewayMessage) => void): () => void {
    this.receivedHandlers.push(handler);
    return () => {
      const idx = this.receivedHandlers.indexOf(handler);
      if (idx >= 0) {
        this.receivedHandlers.splice(idx, 1);
      }
    };
  }

  emitIncoming(message: GatewayMessage): void {
    for (const handler of this.receivedHandlers) {
      handler(message);
    }
  }
}

function createFreshAuth(attempt: number, sessionId: string) {
  return {
    ak: "ak_live_1234",
    tenant_id: "tenant-a",
    client_id: "client-a",
    session_id: sessionId,
    timestamp: 1_762_000_000 + attempt,
    nonce: `nonce-${attempt}`,
    signature: `sig-${attempt}`
  };
}

async function waitFor(predicate: () => boolean): Promise<void> {
  for (let index = 0; index < 40; index += 1) {
    if (predicate()) {
      return;
    }
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
  }
  throw new Error("Timed out waiting for runtime condition.");
}

describe("BridgeSessionRuntime integration", () => {
  it("streams one session request/response over long-lived transport", () => {
    const transport = new MemoryGatewayTransport();
    const runtime = createBridgeRuntime({ transport });
    const inboundEvents: Array<{ topic: string; data: Record<string, unknown> }> = [];

    runtime.subscribe((event) => {
      if (event.name === "gateway.inbound") {
        inboundEvents.push(event.payload);
      }
    });

    runtime.init();
    runtime.start();
    runtime.startSession("session-1", "trace-1");
    runtime.sendOpenCode({
      type: OPCODE_TYPES.TURN_REQUEST,
      payload: {
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 1,
        trace_id: "trace-1",
        prompt: "hello"
      }
    });

    expect(transport.connectCalls).toBe(1);
    expect(transport.sent.some((message) => message.topic === "skill.session.start")).toBe(
      true
    );
    expect(transport.sent.some((message) => message.topic === GATEWAY_TOPICS.TURN_REQUEST)).toBe(
      true
    );

    transport.emitIncoming({
      topic: GATEWAY_TOPICS.TURN_DELTA,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 2,
        trace_id: "trace-1",
        delta: "he"
      }
    });
    transport.emitIncoming({
      topic: GATEWAY_TOPICS.TURN_FINAL,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 3,
        trace_id: "trace-1",
        content: "hello"
      }
    });
    transport.emitIncoming({
      topic: GATEWAY_TOPICS.TURN_COMPLETED,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 4,
        trace_id: "trace-1"
      }
    });

    expect(inboundEvents.map((item) => item.topic)).toEqual([
      OPCODE_TYPES.TURN_DELTA,
      OPCODE_TYPES.TURN_FINAL,
      OPCODE_TYPES.TURN_COMPLETED
    ]);
  });

  it("enforces single in-flight turn with BUSY errors", () => {
    const transport = new MemoryGatewayTransport();
    const runtime = createBridgeRuntime({ transport });
    const runtimeErrors: Array<{ code: string; message: string }> = [];

    runtime.subscribe((event) => {
      if (event.name === "runtime.error") {
        runtimeErrors.push(event.payload);
      }
    });

    runtime.init();
    runtime.start();
    runtime.startSession("session-1", "trace-1");

    runtime.sendOpenCode({
      type: OPCODE_TYPES.TURN_REQUEST,
      payload: {
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 1,
        trace_id: "trace-1",
        prompt: "first"
      }
    });
    runtime.sendOpenCode({
      type: OPCODE_TYPES.TURN_REQUEST,
      payload: {
        session_id: "session-1",
        turn_id: "turn-2",
        seq: 2,
        trace_id: "trace-1",
        prompt: "second"
      }
    });

    const requestMessages = transport.sent.filter(
      (message) => message.topic === GATEWAY_TOPICS.TURN_REQUEST
    );
    expect(requestMessages).toHaveLength(1);
    expect(runtimeErrors.some((event) => event.code === "BUSY")).toBe(true);

    transport.emitIncoming({
      topic: GATEWAY_TOPICS.TURN_COMPLETED,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 2,
        trace_id: "trace-1"
      }
    });

    runtime.sendOpenCode({
      type: OPCODE_TYPES.TURN_REQUEST,
      payload: {
        session_id: "session-1",
        turn_id: "turn-2",
        seq: 3,
        trace_id: "trace-1",
        prompt: "second"
      }
    });

    const afterCompleted = transport.sent.filter(
      (message) => message.topic === GATEWAY_TOPICS.TURN_REQUEST
    );
    expect(afterCompleted).toHaveLength(2);
  });

  it("drops duplicate seq and signals compensation for seq gaps", () => {
    const transport = new MemoryGatewayTransport();
    const runtime = createBridgeRuntime({ transport });
    const inboundEvents: Array<{ topic: string; data: Record<string, unknown> }> = [];
    const errors: Array<{ code: string; message: string }> = [];

    runtime.subscribe((event) => {
      if (event.name === "gateway.inbound") {
        inboundEvents.push(event.payload);
      }
      if (event.name === "runtime.error") {
        errors.push(event.payload);
      }
    });

    runtime.init();
    runtime.start();
    runtime.startSession("session-1", "trace-1");
    runtime.sendOpenCode({
      type: OPCODE_TYPES.TURN_REQUEST,
      payload: {
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 1,
        trace_id: "trace-1",
        prompt: "hello"
      }
    });

    transport.emitIncoming({
      topic: GATEWAY_TOPICS.TURN_DELTA,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 2,
        trace_id: "trace-1",
        delta: "h"
      }
    });
    transport.emitIncoming({
      topic: GATEWAY_TOPICS.TURN_DELTA,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 2,
        trace_id: "trace-1",
        delta: "e"
      }
    });
    transport.emitIncoming({
      topic: GATEWAY_TOPICS.TURN_DELTA,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 4,
        trace_id: "trace-1",
        delta: "l"
      }
    });
    transport.emitIncoming({
      topic: GATEWAY_TOPICS.TURN_DELTA,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 5,
        trace_id: "trace-1",
        delta: "blocked"
      }
    });
    transport.emitIncoming({
      topic: GATEWAY_TOPICS.TURN_DELTA,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 3,
        trace_id: "trace-1",
        delta: "compensated"
      }
    });

    expect(errors.some((item) => item.code === "SEQ_DUPLICATE_DROPPED")).toBe(true);
    expect(
      errors.some((item) => item.code === "SEQ_GAP_COMPENSATION_REQUIRED")
    ).toBe(true);
    expect(inboundEvents.map((event) => event.data.seq)).toEqual([2, 3]);
    const compensation = transport.sent.filter(
      (message) => message.topic === "skill.turn.compensate"
    );
    expect(compensation).toHaveLength(1);
    expect(compensation[0]?.data).toMatchObject({
      session_id: "session-1",
      turn_id: "turn-1",
      expected_seq: 3,
      incoming_seq: 4
    });
  });

  it("retries reconnect with bounded backoff and emits resumed", async () => {
    const transport = new MemoryGatewayTransport();
    transport.reconnectFailuresRemaining = 1;
    const runtime = createBridgeRuntime({
      transport,
      reconnectPolicy: {
        maxAttempts: 3,
        baseDelayMs: 1,
        maxDelayMs: 1
      },
      sleep: async () => {},
      random: () => 0,
      freshAuthFactory: ({ attempt, session_id }) => createFreshAuth(attempt, session_id)
    });
    const reconnecting: Array<{
      attempt: number;
      fresh_auth?: { nonce?: string };
      resume_anchor?: ResumeAnchor;
    }> = [];
    const resumed: Array<{ attempt: number; resume_anchor: ResumeAnchor }> = [];
    const failed: Array<{
      attempt: number;
      reason_code: string;
      next_action: string;
      retryable: boolean;
    }> = [];

    runtime.subscribe((event) => {
      if (event.name === "runtime.reconnecting") {
        reconnecting.push(event.payload);
      }
      if (event.name === "runtime.resumed") {
        resumed.push(event.payload);
      }
      if (event.name === "runtime.failed") {
        failed.push(event.payload);
      }
    });

    runtime.init();
    runtime.start();
    runtime.startSession("session-1", "trace-1");
    runtime.sendOpenCode({
      type: OPCODE_TYPES.TURN_REQUEST,
      payload: {
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 1,
        trace_id: "trace-1",
        prompt: "hello"
      }
    });

    runtime.onReconnect({
      reason: "gateway_disconnected"
    });

    await waitFor(() => resumed.length === 1);

    expect(reconnecting).toHaveLength(2);
    expect(reconnecting[0]?.fresh_auth?.nonce).toBe("nonce-1");
    expect(reconnecting[1]?.fresh_auth?.nonce).toBe("nonce-2");
    expect(reconnecting[0]?.resume_anchor).toMatchObject({
      session_id: "session-1",
      turn_id: "turn-1",
      seq: 1
    });
    expect(resumed[0]?.resume_anchor).toMatchObject({
      session_id: "session-1",
      turn_id: "turn-1",
      seq: 1
    });
    expect(transport.reconnectInputs).toHaveLength(2);
    expect(transport.reconnectInputs[0]?.fresh_auth.nonce).toBe("nonce-1");
    expect(transport.reconnectInputs[1]?.fresh_auth.nonce).toBe("nonce-2");
    expect(transport.resumeAnchors).toHaveLength(1);
    expect(failed).toHaveLength(0);
  });

  it("enforces single reconnect owner and emits deterministic terminal failure", async () => {
    const transport = new MemoryGatewayTransport();
    transport.reconnectFailuresRemaining = 10;
    const runtime = createBridgeRuntime({
      transport,
      reconnectPolicy: {
        maxAttempts: 3,
        baseDelayMs: 1,
        maxDelayMs: 1
      },
      sleep: async () => {},
      random: () => 0,
      freshAuthFactory: ({ attempt, session_id }) => createFreshAuth(attempt, session_id)
    });
    const reconnecting: Array<{ attempt: number }> = [];
    const failed: Array<{
      attempt: number;
      reason_code: string;
      next_action: string;
      retryable: boolean;
    }> = [];

    runtime.subscribe((event) => {
      if (event.name === "runtime.reconnecting") {
        reconnecting.push(event.payload);
      }
      if (event.name === "runtime.failed") {
        failed.push(event.payload);
      }
    });

    runtime.init();
    runtime.start();
    runtime.startSession("session-1", "trace-1");
    runtime.sendOpenCode({
      type: OPCODE_TYPES.TURN_REQUEST,
      payload: {
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 1,
        trace_id: "trace-1",
        prompt: "hello"
      }
    });

    runtime.onReconnect({ reason: "gateway_disconnected" });
    runtime.onReconnect({ reason: "parallel_owner_should_be_ignored" });

    await waitFor(() => failed.length === 1);

    expect(reconnecting).toHaveLength(3);
    expect(transport.reconnectInputs).toHaveLength(3);
    expect(failed[0]).toMatchObject({
      reason_code: "RETRY_BUDGET_EXHAUSTED",
      next_action: "restart_session",
      retryable: false
    });
  });
});
