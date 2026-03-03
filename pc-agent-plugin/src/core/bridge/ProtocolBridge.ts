export interface OpenCodeMessage {
  type: string;
  payload: Record<string, unknown>;
}

export interface GatewayMessage {
  topic: string;
  data: Record<string, unknown>;
}

export interface ProtocolBridge {
  toGateway(message: OpenCodeMessage): GatewayMessage;
  fromGateway(message: GatewayMessage): OpenCodeMessage;
}

export class JsonProtocolBridge implements ProtocolBridge {
  toGateway(message: OpenCodeMessage): GatewayMessage {
    return {
      topic: message.type,
      data: { ...message.payload }
    };
  }

  fromGateway(message: GatewayMessage): OpenCodeMessage {
    return {
      type: message.topic,
      payload: { ...message.data }
    };
  }
}
