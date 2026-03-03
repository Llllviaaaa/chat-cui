import { describe, expect, it } from "vitest";
import {
  BRIDGE_PROTOCOL_VERSION,
  GATEWAY_TOPICS,
  JsonProtocolBridge,
  OPCODE_TYPES
} from "../../src/core/bridge/ProtocolBridge";

describe("BridgeStreamContract", () => {
  it("supports command family mapping", () => {
    const bridge = new JsonProtocolBridge();
    const gateway = bridge.toGateway({
      type: OPCODE_TYPES.TURN_REQUEST,
      payload: {
        session_id: "s1",
        turn_id: "t1",
        seq: 1,
        trace_id: "tr1",
        prompt: "hello"
      }
    });

    expect(gateway.topic).toBe(GATEWAY_TOPICS.TURN_REQUEST);
    expect(gateway.data.protocol_version).toBe(BRIDGE_PROTOCOL_VERSION);
  });

  it("supports response family mapping", () => {
    const bridge = new JsonProtocolBridge();
    const delta = bridge.fromGateway({
      topic: GATEWAY_TOPICS.TURN_DELTA,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "s1",
        turn_id: "t1",
        seq: 2,
        trace_id: "tr1",
        delta: "h"
      }
    });
    const completed = bridge.fromGateway({
      topic: GATEWAY_TOPICS.TURN_COMPLETED,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "s1",
        turn_id: "t1",
        seq: 3,
        trace_id: "tr1"
      }
    });

    expect(delta.type).toBe(OPCODE_TYPES.TURN_DELTA);
    expect(completed.type).toBe(OPCODE_TYPES.TURN_COMPLETED);
  });

  it("supports error family mapping", () => {
    const bridge = new JsonProtocolBridge();
    const unsupported = bridge.fromGateway({
      topic: "skill.turn.unknown",
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        session_id: "s1",
        turn_id: "t1",
        seq: 2,
        trace_id: "tr1"
      }
    });

    expect(unsupported.type).toBe(OPCODE_TYPES.TURN_ERROR);
    expect(unsupported.payload.code).toBe("UNSUPPORTED_EVENT");
  });
});
