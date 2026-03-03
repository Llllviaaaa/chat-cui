import { describe, expect, it } from "vitest";
import {
  BRIDGE_PROTOCOL_VERSION,
  GATEWAY_TOPICS,
  OPCODE_TYPES,
  type GatewayMessage
} from "../../../src/core/bridge/ProtocolBridge";
import { createBridgeRuntime } from "../../../src/core/runtime/BridgeRuntimeFactory";
import type { SessionGatewayTransport } from "../../../src/core/runtime/SessionGatewayTransport";

class MemoryGatewayTransport implements SessionGatewayTransport {
  readonly sent: GatewayMessage[] = [];
  readonly receivedHandlers: Array<(message: GatewayMessage) => void> = [];
  connectCalls = 0;
  disconnectCalls = 0;

  connect(): void {
    this.connectCalls += 1;
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

  it("reports sequence anomalies but continues stream processing", () => {
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

    expect(errors.some((item) => item.code === "SEQ_ANOMALY")).toBe(true);
    expect(inboundEvents).toHaveLength(2);
  });
});
