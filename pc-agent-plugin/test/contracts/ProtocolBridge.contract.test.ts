import { describe, expect, it } from "vitest";
import {
  BRIDGE_PROTOCOL_VERSION,
  GATEWAY_TOPICS,
  JsonProtocolBridge,
  OPCODE_TYPES,
  type OpenCodeMessage
} from "../../src/core/bridge/ProtocolBridge";

describe("ProtocolBridge contracts", () => {
  it("maps turn.request payload to gateway topic and preserves required fields", () => {
    const bridge = new JsonProtocolBridge();
    const message: OpenCodeMessage = {
      type: OPCODE_TYPES.TURN_REQUEST,
      payload: {
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 1,
        trace_id: "trace-1",
        prompt: "hello"
      }
    };

    const gateway = bridge.toGateway(message);
    expect(gateway.topic).toBe(GATEWAY_TOPICS.TURN_REQUEST);
    expect(gateway.data.protocol_version).toBe(BRIDGE_PROTOCOL_VERSION);
    expect(gateway.data.session_id).toBe("session-1");
    expect(gateway.data.turn_id).toBe("turn-1");
    expect(gateway.data.seq).toBe(1);
    expect(gateway.data.trace_id).toBe("trace-1");
    expect(gateway.data.prompt).toBe("hello");

    const back = bridge.fromGateway(gateway);
    expect(back.type).toBe(OPCODE_TYPES.TURN_REQUEST);
    expect(back.payload.session_id).toBe("session-1");
    expect(back.payload.turn_id).toBe("turn-1");
    expect(back.payload.seq).toBe(1);
    expect(back.payload.trace_id).toBe("trace-1");
    expect(back.payload.prompt).toBe("hello");
  });

  it("roundtrips stream delta/final/completed topics", () => {
    const bridge = new JsonProtocolBridge();

    const delta = bridge.fromGateway({
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
    expect(delta.type).toBe(OPCODE_TYPES.TURN_DELTA);
    expect(delta.payload.delta).toBe("he");

    const final = bridge.fromGateway({
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
    expect(final.type).toBe(OPCODE_TYPES.TURN_FINAL);
    expect(final.payload.content).toBe("hello");

    const completed = bridge.fromGateway({
      topic: GATEWAY_TOPICS.TURN_COMPLETED,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 4,
        trace_id: "trace-1"
      }
    });
    expect(completed.type).toBe(OPCODE_TYPES.TURN_COMPLETED);
    expect(completed.payload.seq).toBe(4);
  });

  it("maps unknown opencode event to structured unsupported gateway topic", () => {
    const bridge = new JsonProtocolBridge();
    const gateway = bridge.toGateway({
      type: "turn.unknown",
      payload: {
        trace_id: "trace-unknown"
      }
    });

    expect(gateway.topic).toBe(GATEWAY_TOPICS.TURN_UNSUPPORTED);
    expect(gateway.data.code).toBe("UNSUPPORTED_EVENT");
    expect(gateway.data.event_type).toBe("turn.unknown");
    expect(gateway.data.trace_id).toBe("trace-unknown");
  });

  it("maps version mismatch gateway message to structured turn.error", () => {
    const bridge = new JsonProtocolBridge();
    const error = bridge.fromGateway({
      topic: GATEWAY_TOPICS.TURN_DELTA,
      data: {
        protocol_version: "bridge.v2",
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 2,
        trace_id: "trace-1",
        delta: "oops"
      }
    });

    expect(error.type).toBe(OPCODE_TYPES.TURN_ERROR);
    expect(error.payload.code).toBe("VERSION_MISMATCH");
    expect(error.payload.event_type).toBe(GATEWAY_TOPICS.TURN_DELTA);
  });

  it("preserves unknown fields in extensions", () => {
    const bridge = new JsonProtocolBridge();
    const message = bridge.fromGateway({
      topic: GATEWAY_TOPICS.TURN_DELTA,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "session-1",
        turn_id: "turn-1",
        seq: 2,
        trace_id: "trace-1",
        delta: "hello",
        custom_field: "x"
      }
    });

    expect(message.type).toBe(OPCODE_TYPES.TURN_DELTA);
    expect(message.payload.extensions).toMatchObject({
      custom_field: "x"
    });
  });
});
