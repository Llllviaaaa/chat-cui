export type RuntimeHealth = "healthy" | "degraded" | "down";

export type PluginEventName =
  | "runtime.started"
  | "runtime.stopped"
  | "runtime.disposed"
  | "runtime.reconnecting"
  | "runtime.health"
  | "runtime.error"
  | "gateway.outbound"
  | "gateway.inbound";

export interface PluginEventPayloadMap {
  "runtime.started": { state: string };
  "runtime.stopped": { state: string };
  "runtime.disposed": { state: string };
  "runtime.reconnecting": { attempt: number };
  "runtime.health": { health: RuntimeHealth; detail?: string };
  "runtime.error": { code: string; message: string };
  "gateway.outbound": { topic: string; data: Record<string, unknown> };
  "gateway.inbound": { topic: string; data: Record<string, unknown> };
}

export type PluginEvent<K extends PluginEventName = PluginEventName> = {
  name: K;
  payload: PluginEventPayloadMap[K];
};

export type PluginEventHandler<K extends PluginEventName = PluginEventName> = (
  event: PluginEvent<K>
) => void;
