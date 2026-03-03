export interface OpenCodeMessage {
  type: string;
  payload: Record<string, unknown>;
}

export interface GatewayMessage {
  topic: string;
  data: Record<string, unknown>;
}

export const BRIDGE_PROTOCOL_VERSION = "bridge.v1" as const;

export const GATEWAY_TOPICS = {
  TURN_REQUEST: "skill.turn.request",
  TURN_DELTA: "skill.turn.delta",
  TURN_FINAL: "skill.turn.final",
  TURN_COMPLETED: "skill.turn.completed",
  TURN_ERROR: "skill.turn.error",
  TURN_UNSUPPORTED: "skill.turn.unsupported"
} as const;

export const OPCODE_TYPES = {
  TURN_REQUEST: "turn.request",
  TURN_DELTA: "turn.delta",
  TURN_FINAL: "turn.final",
  TURN_COMPLETED: "turn.completed",
  TURN_ERROR: "turn.error"
} as const;

const KNOWN_TO_GATEWAY: Record<string, string> = {
  [OPCODE_TYPES.TURN_REQUEST]: GATEWAY_TOPICS.TURN_REQUEST,
  [OPCODE_TYPES.TURN_DELTA]: GATEWAY_TOPICS.TURN_DELTA,
  [OPCODE_TYPES.TURN_FINAL]: GATEWAY_TOPICS.TURN_FINAL,
  [OPCODE_TYPES.TURN_COMPLETED]: GATEWAY_TOPICS.TURN_COMPLETED,
  [OPCODE_TYPES.TURN_ERROR]: GATEWAY_TOPICS.TURN_ERROR
};

const KNOWN_FROM_GATEWAY: Record<string, string> = {
  [GATEWAY_TOPICS.TURN_REQUEST]: OPCODE_TYPES.TURN_REQUEST,
  [GATEWAY_TOPICS.TURN_DELTA]: OPCODE_TYPES.TURN_DELTA,
  [GATEWAY_TOPICS.TURN_FINAL]: OPCODE_TYPES.TURN_FINAL,
  [GATEWAY_TOPICS.TURN_COMPLETED]: OPCODE_TYPES.TURN_COMPLETED,
  [GATEWAY_TOPICS.TURN_ERROR]: OPCODE_TYPES.TURN_ERROR
};

export interface ProtocolBridge {
  toGateway(message: OpenCodeMessage): GatewayMessage;
  fromGateway(message: GatewayMessage): OpenCodeMessage;
}

export class JsonProtocolBridge implements ProtocolBridge {
  toGateway(message: OpenCodeMessage): GatewayMessage {
    const mappedTopic = KNOWN_TO_GATEWAY[message.type];
    if (!mappedTopic) {
      return {
        topic: GATEWAY_TOPICS.TURN_UNSUPPORTED,
        data: {
          protocol_version: BRIDGE_PROTOCOL_VERSION,
          code: "UNSUPPORTED_EVENT",
          message: "Unsupported opencode event type",
          event_type: message.type,
          trace_id: getString(message.payload.trace_id)
        }
      };
    }

    return {
      topic: mappedTopic,
      data: {
        protocol_version: BRIDGE_PROTOCOL_VERSION,
        ...message.payload
      }
    };
  }

  fromGateway(message: GatewayMessage): OpenCodeMessage {
    const version = getString(message.data.protocol_version);
    if (version && version !== BRIDGE_PROTOCOL_VERSION) {
      return toVersionMismatchError(version, message.topic, message.data);
    }

    if (message.topic === GATEWAY_TOPICS.TURN_UNSUPPORTED) {
      return toUnsupportedError(getString(message.data.event_type) ?? message.topic, message.data);
    }

    const mappedType = KNOWN_FROM_GATEWAY[message.topic];
    if (!mappedType) {
      return toUnsupportedError(message.topic, message.data);
    }

    const payload = {
      ...message.data,
      extensions: extractExtensions(message.data, mappedType)
    };

    return {
      type: mappedType,
      payload
    };
  }
}

function extractExtensions(
  data: Record<string, unknown>,
  mappedType: string
): Record<string, unknown> | undefined {
  const allowed = new Set<string>([
    "protocol_version",
    "session_id",
    "turn_id",
    "seq",
    "trace_id",
    "extensions"
  ]);

  if (mappedType === OPCODE_TYPES.TURN_REQUEST) {
    allowed.add("prompt");
  }
  if (mappedType === OPCODE_TYPES.TURN_DELTA) {
    allowed.add("delta");
  }
  if (mappedType === OPCODE_TYPES.TURN_FINAL) {
    allowed.add("content");
  }
  if (mappedType === OPCODE_TYPES.TURN_ERROR) {
    allowed.add("code");
    allowed.add("message");
    allowed.add("retryable");
    allowed.add("event_type");
  }

  const extensions: Record<string, unknown> = {
    ...(isRecord(data.extensions) ? data.extensions : {})
  };

  for (const [key, value] of Object.entries(data)) {
    if (!allowed.has(key)) {
      extensions[key] = value;
    }
  }

  return Object.keys(extensions).length > 0 ? extensions : undefined;
}

function toUnsupportedError(
  eventType: string,
  data: Record<string, unknown>
): OpenCodeMessage {
  return {
    type: OPCODE_TYPES.TURN_ERROR,
    payload: {
      session_id: data.session_id,
      turn_id: data.turn_id,
      seq: data.seq,
      trace_id: data.trace_id,
      code: "UNSUPPORTED_EVENT",
      message: "Unsupported gateway event type",
      event_type: eventType,
      extensions: extractExtensions(data, OPCODE_TYPES.TURN_ERROR)
    }
  };
}

function toVersionMismatchError(
  version: string,
  topic: string,
  data: Record<string, unknown>
): OpenCodeMessage {
  return {
    type: OPCODE_TYPES.TURN_ERROR,
    payload: {
      session_id: data.session_id,
      turn_id: data.turn_id,
      seq: data.seq,
      trace_id: data.trace_id,
      code: "VERSION_MISMATCH",
      message: `Unsupported protocol version: ${version}`,
      event_type: topic,
      extensions: extractExtensions(data, OPCODE_TYPES.TURN_ERROR)
    }
  };
}

function getString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value : undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}
