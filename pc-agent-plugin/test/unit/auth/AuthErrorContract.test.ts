import { describe, expect, it } from "vitest";
import {
  AUTH_V1_CODES,
  mapAuthFailure
} from "../../../src/core/auth/AuthFailureMapper";

describe("AuthErrorContract", () => {
  it("covers every AUTH_V1 failure code", () => {
    expect([...AUTH_V1_CODES]).toEqual([
      "AUTH_V1_MISSING_CREDENTIAL",
      "AUTH_V1_INVALID_SIGNATURE",
      "AUTH_V1_TIMESTAMP_OUT_OF_WINDOW",
      "AUTH_V1_REPLAY_DETECTED",
      "AUTH_V1_COOLDOWN_ACTIVE",
      "AUTH_V1_CREDENTIAL_DISABLED",
      "AUTH_V1_PERMISSION_DENIED"
    ]);
  });

  it("emits required envelope fields", () => {
    for (const code of AUTH_V1_CODES) {
      const envelope = mapAuthFailure(code, "trace-1", "session-1", "debug-1", 7);
      expect(envelope.error_code).toBe(code);
      expect(envelope.message.length).toBeGreaterThan(0);
      expect(envelope.next_action.length).toBeGreaterThan(0);
      expect(envelope.trace_id).toBe("trace-1");
      expect(envelope.session_id).toBe("session-1");
      expect(envelope.debug_id).toBe("debug-1");
    }
  });

  it("only cooldown includes retry_after", () => {
    const cooldown = mapAuthFailure(
      "AUTH_V1_COOLDOWN_ACTIVE",
      "trace-1",
      "session-1",
      "debug-1",
      5
    );
    expect(cooldown.retry_after).toBe(5);

    const normal = mapAuthFailure(
      "AUTH_V1_INVALID_SIGNATURE",
      "trace-1",
      "session-1",
      "debug-1"
    );
    expect(normal.retry_after).toBeUndefined();
  });
});
