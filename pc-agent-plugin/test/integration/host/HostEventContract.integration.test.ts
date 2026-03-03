import { describe, expect, it } from "vitest";
import { HostPluginAdapter } from "../../../src/host-adapter/HostPluginAdapter";
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
      type: "opencode.message",
      payload: {
        type: "question.created",
        payload: { text: "hello opencode" }
      }
    });

    const outbound = events.find((event) => event.type === "gateway.outbound");
    expect(outbound).toBeDefined();
    expect(outbound?.payload.topic).toBe("question.created");
    expect(outbound?.payload.data).toMatchObject({ text: "hello opencode" });
  });

  it("maps gateway host event to inbound plugin event", () => {
    const { adapter, events } = setupAdapter();

    adapter.onHostEvent({
      type: "gateway.message",
      payload: {
        topic: "skill.delta",
        data: { text: "delta token" }
      }
    });

    const inbound = events.find((event) => event.type === "gateway.inbound");
    expect(inbound).toBeDefined();
    expect(inbound?.payload.topic).toBe("skill.delta");
    expect(inbound?.payload.data).toMatchObject({ text: "delta token" });
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
