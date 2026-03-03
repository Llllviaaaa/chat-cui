import { describe, expect, it } from "vitest";
import { HostPluginAdapter } from "../../../src/host-adapter/HostPluginAdapter";
import {
  BRIDGE_PROTOCOL_VERSION,
  GATEWAY_TOPICS,
  OPCODE_TYPES
} from "../../../src/core/bridge/ProtocolBridge";
import type { HostOutboundEvent } from "../../../src/host-adapter/contracts/HostPluginContract";

function setupAdapter() {
  const events: HostOutboundEvent[] = [];
  const adapter = new HostPluginAdapter({
    emitHostEvent: (event) => events.push(event)
  });
  adapter.init();
  adapter.start();
  return { adapter, events };
}

describe("HostEventContract integration", () => {
  it("maps host opencode event to gateway outbound plugin event", () => {
    const { adapter, events } = setupAdapter();
    adapter.onHostEvent({
      type: "session.start",
      payload: {
        session_id: "session-1",
        trace_id: "trace-1"
      }
    });

    adapter.onHostEvent({
      type: "opencode.message",
      payload: {
        type: OPCODE_TYPES.TURN_REQUEST,
        payload: {
          session_id: "session-1",
          turn_id: "turn-1",
          seq: 1,
          trace_id: "trace-1",
          prompt: "hello opencode"
        }
      }
    });

    const outbound = events.find((event) => event.type === "gateway.outbound");
    expect(outbound).toBeDefined();
    expect(outbound?.payload.topic).toBe("skill.session.start");

    const request = events.find(
      (event) =>
        event.type === "gateway.outbound" &&
        event.payload.topic === GATEWAY_TOPICS.TURN_REQUEST
    );
    expect(request).toBeDefined();
    expect(request?.payload.data).toMatchObject({
      protocol_version: BRIDGE_PROTOCOL_VERSION,
      session_id: "session-1",
      turn_id: "turn-1",
      seq: 1,
      trace_id: "trace-1",
      prompt: "hello opencode"
    });
  });

  it("maps gateway host event to inbound plugin event", () => {
    const { adapter, events } = setupAdapter();

    adapter.onHostEvent({
      type: "gateway.message",
      payload: {
        topic: GATEWAY_TOPICS.TURN_DELTA,
        data: {
          protocol_version: BRIDGE_PROTOCOL_VERSION,
          session_id: "session-1",
          turn_id: "turn-1",
          seq: 2,
          trace_id: "trace-1",
          delta: "delta token"
        }
      }
    });

    const inbound = events.find((event) => event.type === "gateway.inbound");
    expect(inbound).toBeDefined();
    expect(inbound?.payload.topic).toBe(OPCODE_TYPES.TURN_DELTA);
    expect(inbound?.payload.data).toMatchObject({ delta: "delta token" });
  });

  it("emits deterministic error for unsupported host events", () => {
    const { adapter, events } = setupAdapter();

    adapter.onHostEvent({
      type: "unknown.host.event",
      payload: { trace: "unsupported-1" }
    });

    const runtimeError = events.find((event) => event.type === "runtime.error");
    expect(runtimeError).toBeDefined();
    expect(runtimeError?.payload.code).toBe("HOST_EVENT_UNSUPPORTED");
    expect(runtimeError?.payload.message).toContain("unknown.host.event");
  });
});
