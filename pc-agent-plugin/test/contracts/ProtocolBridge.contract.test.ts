import { describe, expect, it } from "vitest";
import {
  JsonProtocolBridge,
  type OpenCodeMessage
} from "../../src/core/bridge/ProtocolBridge";

describe("ProtocolBridge contracts", () => {
  it("maps opencode payload to gateway message and back", () => {
    const bridge = new JsonProtocolBridge();
    const message: OpenCodeMessage = {
      type: "question",
      payload: { text: "hello" }
    };

    const gateway = bridge.toGateway(message);
    expect(gateway.topic).toBe("question");
    expect(gateway.data.text).toBe("hello");

    const back = bridge.fromGateway(gateway);
    expect(back.type).toBe("question");
    expect(back.payload.text).toBe("hello");
  });
});
