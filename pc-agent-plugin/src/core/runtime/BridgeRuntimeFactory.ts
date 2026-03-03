import { JsonProtocolBridge, type ProtocolBridge } from "../bridge/ProtocolBridge";
import { LifecycleStateMachine } from "../lifecycle/LifecycleStateMachine";
import { BridgeRuntime } from "./BridgeRuntime";
import type { SessionGatewayTransport } from "./SessionGatewayTransport";

export interface BridgeRuntimeFactoryOptions {
  bridge?: ProtocolBridge;
  lifecycle?: LifecycleStateMachine;
  transport?: SessionGatewayTransport;
}

export function createBridgeRuntime(
  options: BridgeRuntimeFactoryOptions = {}
): BridgeRuntime {
  return new BridgeRuntime({
    bridge: options.bridge ?? new JsonProtocolBridge(),
    lifecycle: options.lifecycle ?? new LifecycleStateMachine(),
    transport: options.transport
  });
}
