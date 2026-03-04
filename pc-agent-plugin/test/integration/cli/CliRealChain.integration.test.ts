import { describe, expect, it } from "vitest";
import { runSession } from "../../../src/cli/commands/runSession";

class StubCredentialProvider {
  constructor(private readonly secret: string) {}

  async readSecret(): Promise<string> {
    return this.secret;
  }

  async upsertSecret(): Promise<void> {
    // no-op for integration tests
  }

  async deleteSecret(): Promise<void> {
    // no-op for integration tests
  }
}

function createLogger() {
  const info: string[] = [];
  const error: string[] = [];
  return {
    info,
    error,
    logger: {
      info: (message: string) => info.push(message),
      error: (message: string) => error.push(message)
    }
  };
}

function baseEnv(overrides: NodeJS.ProcessEnv = {}): NodeJS.ProcessEnv {
  return {
    CHATCUI_TENANT_ID: "tenant-a",
    CHATCUI_CLIENT_ID: "client-a",
    CHATCUI_AK: "ak_live_1234",
    CHATCUI_SECRET_REF: "wincred://tenant-a/client-a",
    CHATCUI_REAL_CHAIN: "true",
    CHATCUI_OPENCODE_ENDPOINT: "http://127.0.0.1:11400",
    CHATCUI_GATEWAY_ENDPOINT: "ws://127.0.0.1:8080/ws",
    CHATCUI_TRACE_ID: "trace-test",
    CHATCUI_SESSION_ID: "session-test",
    CHATCUI_DEBUG_ID: "debug-test",
    ...overrides
  };
}

describe("CliRealChain integration", () => {
  it("rejects missing real-chain endpoint configuration", async () => {
    const logs = createLogger();
    const result = await runSession({
      question: "hello",
      env: baseEnv({ CHATCUI_OPENCODE_ENDPOINT: "" }),
      logger: logs.logger,
      bootstrapOptions: {
        credentialProvider: new StubCredentialProvider("secret-a")
      }
    });

    expect(result.ok).toBe(false);
    if (result.ok) {
      throw new Error("Expected runSession failure for missing chain config");
    }
    expect(result.error.error_code).toBe("AUTH_V1_MISSING_CREDENTIAL");
    expect(result.markers).toContain("real_chain_rejected");
  });

  it("rejects mock-only execution by requiring CHATCUI_REAL_CHAIN=true", async () => {
    const logs = createLogger();
    const result = await runSession({
      question: "hello",
      env: baseEnv({ CHATCUI_REAL_CHAIN: "false" }),
      logger: logs.logger,
      bootstrapOptions: {
        credentialProvider: new StubCredentialProvider("secret-a")
      }
    });

    expect(result.ok).toBe(false);
    if (result.ok) {
      throw new Error("Expected runSession failure for mock-only chain");
    }
    expect(result.error.error_code).toBe("AUTH_V1_PERMISSION_DENIED");
    expect(logs.error.join(" ")).toContain("AUTH_V1_PERMISSION_DENIED");
  });

  it("runs one CLI session on configured real-chain path", async () => {
    const logs = createLogger();
    const result = await runSession({
      question: "hello",
      env: baseEnv(),
      logger: logs.logger,
      bootstrapOptions: {
        credentialProvider: new StubCredentialProvider("secret-a"),
        clock: () => 1_762_000_000,
        nonceFactory: () => "nonce-test"
      }
    });

    expect(result.ok).toBe(true);
    if (!result.ok) {
      throw new Error(`Unexpected runSession failure: ${result.error.error_code}`);
    }
    expect(result.sessionId).toBe("session-test");
    expect(result.traceId).toBe("trace-test");
    expect(result.markers).toContain("real_chain_session_started");
    expect(result.markers).toContain("auth_v1_signature_generated");
    expect(logs.info.join(" ")).toContain("Real chain session started.");
    expect(logs.info.join(" ")).toContain("[plugin-event] runtime.health");
    expect(logs.info.join(" ")).not.toContain("[plugin-event] runtime.failed");
  });
});
