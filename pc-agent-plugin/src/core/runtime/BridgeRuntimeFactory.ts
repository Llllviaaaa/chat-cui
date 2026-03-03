import { JsonProtocolBridge, type ProtocolBridge } from "../bridge/ProtocolBridge";
import { LifecycleStateMachine } from "../lifecycle/LifecycleStateMachine";
import { BridgeRuntime } from "./BridgeRuntime";

export interface BridgeRuntimeFactoryOptions {
  bridge?: ProtocolBridge;
  lifecycle?: LifecycleStateMachine;
}

export function createBridgeRuntime(
  options: BridgeRuntimeFactoryOptions = {}
): BridgeRuntime {
  return new BridgeRuntime({
    bridge: options.bridge ?? new JsonProtocolBridge(),
    lifecycle: options.lifecycle ?? new LifecycleStateMachine()
  });
}
