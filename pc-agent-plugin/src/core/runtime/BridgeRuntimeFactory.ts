import { JsonProtocolBridge, type ProtocolBridge } from "../bridge/ProtocolBridge";
import { LifecycleStateMachine } from "../lifecycle/LifecycleStateMachine";
import {
  BridgeRuntime,
  type ReconnectPolicy,
  type RuntimeFreshAuthFactory
} from "./BridgeRuntime";
import type { SessionGatewayTransport } from "./SessionGatewayTransport";

export interface BridgeRuntimeFactoryOptions {
  bridge?: ProtocolBridge;
  lifecycle?: LifecycleStateMachine;
  transport?: SessionGatewayTransport;
  reconnectPolicy?: Partial<ReconnectPolicy>;
  freshAuthFactory?: RuntimeFreshAuthFactory;
  random?: () => number;
  sleep?: (ms: number) => Promise<void>;
}

export function createBridgeRuntime(
  options: BridgeRuntimeFactoryOptions = {}
): BridgeRuntime {
  return new BridgeRuntime({
    bridge: options.bridge ?? new JsonProtocolBridge(),
    lifecycle: options.lifecycle ?? new LifecycleStateMachine(),
    transport: options.transport,
    reconnectPolicy: options.reconnectPolicy,
    freshAuthFactory: options.freshAuthFactory,
    random: options.random,
    sleep: options.sleep
  });
}
