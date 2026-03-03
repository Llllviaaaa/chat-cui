import { createHmac } from "node:crypto";
import { describe, expect, it } from "vitest";
import {
  AUTH_PAYLOAD_ORDER,
  buildCanonicalPayload
} from "../../../src/core/auth/AuthPayloadBuilder";
import { AuthSigner } from "../../../src/core/auth/AuthSigner";

describe("AuthSignerContract", () => {
  it("uses canonical field ordering from AUTH_V1 spec", () => {
    expect(AUTH_PAYLOAD_ORDER).toEqual([
      "ak",
      "tenant_id",
      "client_id",
      "timestamp",
      "nonce",
      "session_id"
    ]);
  });

  it("builds canonical payload and hmac-sha256 base64 signature", () => {
    const signer = new AuthSigner();
    const fields = {
      ak: "ak_live_1234",
      tenant_id: "tenant-a",
      client_id: "client-a",
      timestamp: 1762435200,
      nonce: "nonce-1",
      session_id: "session-1"
    };

    const payload = buildCanonicalPayload(fields);
    expect(payload).toBe(
      "ak:ak_live_1234\n" +
        "tenant_id:tenant-a\n" +
        "client_id:client-a\n" +
        "timestamp:1762435200\n" +
        "nonce:nonce-1\n" +
        "session_id:session-1"
    );

    const expected = createHmac("sha256", "secret-a")
      .update(payload, "utf8")
      .digest("base64");
    expect(signer.sign(fields, "secret-a")).toBe(expected);
    expect(signer.verify(fields, "secret-a", expected)).toBe(true);
  });

  it("rejects missing fields", () => {
    const signer = new AuthSigner();
    expect(() =>
      signer.sign(
        {
          ak: "",
          tenant_id: "tenant-a",
          client_id: "client-a",
          timestamp: 1,
          nonce: "n",
          session_id: "s"
        },
        "secret"
      )
    ).toThrow("Missing required field: ak");
  });
});
