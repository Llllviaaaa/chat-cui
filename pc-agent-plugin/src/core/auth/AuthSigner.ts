import { createHmac, timingSafeEqual } from "node:crypto";
import { buildCanonicalPayload, type AuthPayloadFields } from "./AuthPayloadBuilder";

export class AuthSigner {
  buildCanonicalPayload(fields: AuthPayloadFields): string {
    return buildCanonicalPayload(fields);
  }

  sign(fields: AuthPayloadFields, secret: string): string {
    const normalizedSecret = secret.trim();
    if (!normalizedSecret) {
      throw new Error("Secret must not be blank");
    }
    const payload = this.buildCanonicalPayload(fields);
    return createHmac("sha256", normalizedSecret).update(payload, "utf8").digest("base64");
  }

  verify(fields: AuthPayloadFields, secret: string, signature: string): boolean {
    try {
      const expected = Buffer.from(this.sign(fields, secret), "base64");
      const provided = Buffer.from(signature, "base64");
      if (expected.length !== provided.length) {
        return false;
      }
      return timingSafeEqual(expected, provided);
    } catch {
      return false;
    }
  }
}
