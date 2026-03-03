import type { BridgeRuntime } from "../core/runtime/BridgeRuntime";

export class HostLifecycleBinding {
  constructor(private readonly runtime: BridgeRuntime) {}

  init(): void {
    this.runtime.init();
  }

  start(): void {
    this.runtime.start();
  }

  stop(): void {
    this.runtime.stop();
  }

  dispose(): void {
    this.runtime.dispose();
  }

  reconnect(): void {
    this.runtime.onReconnect();
  }

  currentState(): string {
    return this.runtime.currentState();
  }
}
