import { describe, expect, it } from "vitest";
import { HostPluginAdapter } from "../../../src/host-adapter/HostPluginAdapter";
import type { HostOutboundEvent } from "../../../src/host-adapter/contracts/HostPluginContract";

function collectHostEvents() {
  const events: HostOutboundEvent[] = [];
  return {
    events,
    emitHostEvent: (event: HostOutboundEvent) => {
      events.push(event);
    }
  };
}

describe("HostLifecycle integration", () => {
  it("wires init/start/stop/dispose lifecycle over shared runtime", () => {
    const sink = collectHostEvents();
    const adapter = new HostPluginAdapter(sink);

    adapter.init();
    adapter.start();
    expect(adapter.currentState()).toBe("running");

    adapter.onHostEvent({ type: "runtime.reconnect" });

    adapter.stop();
    expect(adapter.currentState()).toBe("stopped");
    adapter.dispose();
    expect(adapter.currentState()).toBe("disposed");

    const names = sink.events.map((event) => event.type);
    expect(names).toContain("runtime.started");
    expect(names).toContain("runtime.reconnecting");
    expect(names).toContain("runtime.stopped");
    expect(names).toContain("runtime.disposed");
  });

  it("propagates health and AUTH failure events to host", () => {
    const sink = collectHostEvents();
    const adapter = new HostPluginAdapter(sink);

    adapter.init();
    adapter.start();
    adapter.onHostEvent({
      type: "runtime.health",
      payload: { health: "degraded", detail: "gateway latency high" }
    });
    adapter.onHostEvent({
      type: "runtime.error",
      payload: {
        code: "AUTH_V1_INVALID_SIGNATURE",
        message: "Authentication failed."
      }
    });

    const healthEvent = sink.events.find((event) => event.type === "runtime.health");
    expect(healthEvent).toBeDefined();
    expect(healthEvent?.payload.health).toBe("degraded");
    expect(healthEvent?.payload.detail).toBe("gateway latency high");

    const authError = sink.events.find((event) => event.type === "runtime.error");
    expect(authError).toBeDefined();
    expect(authError?.payload.code).toBe("AUTH_V1_INVALID_SIGNATURE");
    expect(authError?.payload.message).toBe("Authentication failed.");
  });
});
