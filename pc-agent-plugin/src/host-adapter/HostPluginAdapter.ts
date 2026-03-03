import {
  createBridgeRuntime,
  type BridgeRuntimeFactoryOptions
} from "../core/runtime/BridgeRuntimeFactory";
import type { BridgeRuntime } from "../core/runtime/BridgeRuntime";
import { HostEventBridge } from "./HostEventBridge";
import { HostLifecycleBinding } from "./HostLifecycleBinding";
import type {
  HostInboundEvent,
  HostPluginAdapterContract,
  HostPluginOptions
} from "./contracts/HostPluginContract";

export interface HostPluginAdapterOptions extends HostPluginOptions {
  runtime?: BridgeRuntime;
  runtimeFactoryOptions?: BridgeRuntimeFactoryOptions;
  eventBridge?: HostEventBridge;
  lifecycleBinding?: HostLifecycleBinding;
}

export class HostPluginAdapter implements HostPluginAdapterContract {
  private readonly runtime: BridgeRuntime;
  private readonly eventBridge: HostEventBridge;
  private readonly lifecycleBinding: HostLifecycleBinding;
  private readonly emitHostEvent: HostPluginOptions["emitHostEvent"];
  private unsubscribeFromRuntime?: () => void;
  private initialized = false;

  constructor(options: HostPluginAdapterOptions) {
    this.runtime =
      options.runtime ?? createBridgeRuntime(options.runtimeFactoryOptions);
    this.eventBridge = options.eventBridge ?? new HostEventBridge();
    this.lifecycleBinding =
      options.lifecycleBinding ?? new HostLifecycleBinding(this.runtime);
    this.emitHostEvent = options.emitHostEvent;
  }

  init(): void {
    if (!this.initialized) {
      this.unsubscribeFromRuntime = this.eventBridge.bindRuntime(
        this.runtime,
        this.emitHostEvent
      );
      this.initialized = true;
    }
    this.lifecycleBinding.init();
  }

  start(): void {
    this.lifecycleBinding.start();
  }

  stop(): void {
    this.lifecycleBinding.stop();
  }

  dispose(): void {
    this.lifecycleBinding.dispose();
    this.unsubscribeFromRuntime?.();
    this.unsubscribeFromRuntime = undefined;
    this.initialized = false;
  }

  onHostEvent(event: HostInboundEvent): void {
    this.eventBridge.dispatchHostEvent(this.runtime, event);
  }

  currentState(): string {
    return this.lifecycleBinding.currentState();
  }

  runtimeSource(): string {
    return this.runtime.source;
  }
}
