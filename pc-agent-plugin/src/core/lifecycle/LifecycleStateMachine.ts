export type LifecycleState =
  | "created"
  | "initialized"
  | "running"
  | "stopped"
  | "disposed";

export class LifecycleStateMachine {
  private state: LifecycleState = "created";

  currentState(): LifecycleState {
    return this.state;
  }

  init(): LifecycleState {
    if (this.state !== "created" && this.state !== "stopped") {
      throw new Error(`init transition is invalid from ${this.state}`);
    }
    this.state = "initialized";
    return this.state;
  }

  start(): LifecycleState {
    if (this.state !== "initialized" && this.state !== "stopped") {
      throw new Error(`start transition is invalid from ${this.state}`);
    }
    this.state = "running";
    return this.state;
  }

  stop(): LifecycleState {
    if (this.state !== "running") {
      throw new Error(`stop transition is invalid from ${this.state}`);
    }
    this.state = "stopped";
    return this.state;
  }

  dispose(): LifecycleState {
    if (this.state === "disposed") {
      return this.state;
    }
    this.state = "disposed";
    return this.state;
  }

  onReconnect(): LifecycleState {
    if (this.state !== "running") {
      throw new Error(`reconnect transition is invalid from ${this.state}`);
    }
    this.state = "running";
    return this.state;
  }
}
