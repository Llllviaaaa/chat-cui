import type { PluginEvent } from "../core/events/PluginEvents";
import type { BridgeRuntime } from "../core/runtime/BridgeRuntime";
import type {
  HostInboundEvent,
  HostOutboundEvent
} from "./contracts/HostPluginContract";

export class HostEventBridge {
  bindRuntime(
    runtime: BridgeRuntime,
    emitHostEvent: (event: HostOutboundEvent) => void
  ): () => void {
    return runtime.subscribe((event) => {
      emitHostEvent(this.toHostEvent(event));
    });
  }

  dispatchHostEvent(runtime: BridgeRuntime, event: HostInboundEvent): void {
    switch (event.type) {
      case "session.start":
        runtime.startSession(event.payload.session_id, event.payload.trace_id);
        return;
      case "session.end":
        runtime.endSession(event.payload?.reason);
        return;
      case "opencode.message":
        runtime.sendOpenCode(event.payload);
        return;
      case "gateway.message":
        runtime.receiveGateway(event.payload.topic, event.payload.data);
        return;
      case "runtime.reconnect":
        runtime.onReconnect(event.payload);
        return;
      case "runtime.health":
        runtime.reportHealth(event.payload.health, event.payload.detail);
        return;
      case "runtime.error":
        runtime.reportError(event.payload.code, event.payload.message);
        return;
      default:
        runtime.reportError(
          "HOST_EVENT_UNSUPPORTED",
          `Unsupported host event: ${event.type}`
        );
    }
  }

  private toHostEvent(event: PluginEvent): HostOutboundEvent {
    return {
      type: event.name,
      payload: event.payload as Record<string, unknown>
    };
  }
}
