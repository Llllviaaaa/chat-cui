import { describe, expect, it, vi } from "vitest";
import { AuthSigner } from "../../../src/core/auth/AuthSigner";
import { CliAuthConfigLoader } from "../../../src/cli/config/CliAuthConfigLoader";
import {
  bootstrapRuntime,
  CliRuntimeBootstrapError
} from "../../../src/cli/runtime/CliRuntimeBootstrap";
import { createBridgeRuntime } from "../../../src/core/runtime/BridgeRuntimeFactory";

class StubCredentialProvider {
  constructor(private readonly secret: string) {}

  async readSecret(): Promise<string> {
    return this.secret;
  }

  async upsertSecret(): Promise<void> {
    // no-op for tests
  }

  async deleteSecret(): Promise<void> {
    // no-op for tests
  }
}

describe("CliRuntimeBootstrap", () => {
  it("bootstraps runtime through shared factory and AUTH_V1 signer", async () => {
    const runtime = createBridgeRuntime();
    const runtimeFactory = vi.fn(() => runtime);

    const env = {
      CHATCUI_TENANT_ID: "tenant-a",
      CHATCUI_CLIENT_ID: "client-a",
      CHATCUI_AK: "ak_live_1234",
      CHATCUI_SECRET_REF: "wincred://tenant-a/client-a",
      CHATCUI_REAL_CHAIN: "true",
      CHATCUI_OPENCODE_ENDPOINT: "http://127.0.0.1:11400",
      CHATCUI_GATEWAY_ENDPOINT: "ws://127.0.0.1:8080/ws",
      CHATCUI_NONCE: "nonce-1",
      CHATCUI_SESSION_ID: "session-1",
      CHATCUI_TRACE_ID: "trace-1"
    } satisfies NodeJS.ProcessEnv;

    const result = await bootstrapRuntime({
      env,
      credentialProvider: new StubCredentialProvider("secret-a"),
      runtimeFactory,
      clock: () => 1_762_000_000
    });

    const expectedSignature = new AuthSigner().sign(
      {
        ak: "ak_live_1234",
        tenant_id: "tenant-a",
        client_id: "client-a",
        timestamp: 1_762_000_000,
        nonce: "nonce-1",
        session_id: "session-1"
      },
      "secret-a"
    );

    expect(runtimeFactory).toHaveBeenCalledTimes(1);
    expect(result.runtime.source).toBe("shared-core");
    expect(result.auth.signature).toBe(expectedSignature);
    expect(result.chain.realChain).toBe(true);
  });

  it("rejects disabled credential state from auth config", async () => {
    const env = {
      CHATCUI_TENANT_ID: "tenant-a",
      CHATCUI_CLIENT_ID: "client-a",
      CHATCUI_AK: "ak_live_1234",
      CHATCUI_SECRET_REF: "wincred://tenant-a/client-a",
      CHATCUI_CREDENTIAL_STATE: "disabled",
      CHATCUI_REAL_CHAIN: "true",
      CHATCUI_OPENCODE_ENDPOINT: "http://127.0.0.1:11400",
      CHATCUI_GATEWAY_ENDPOINT: "ws://127.0.0.1:8080/ws"
    } satisfies NodeJS.ProcessEnv;

    await expect(
      bootstrapRuntime({
        env,
        credentialProvider: new StubCredentialProvider("secret-a")
      })
    ).rejects.toMatchObject<CliRuntimeBootstrapError>({
      code: "AUTH_CONFIG_INVALID",
      message: "Credential state DISABLED blocks startup"
    });
  });

  it("redacts secrets from log-safe config output", () => {
    const loader = new CliAuthConfigLoader();
    const safe = loader.sanitizeForLog({
      tenant_id: "tenant-a",
      sk: "sensitive-sk",
      signature: "raw-signature",
      secret_ref: "wincred://tenant/client"
    });

    expect(safe.tenant_id).toBe("tenant-a");
    expect(safe.sk).toBe("***REDACTED***");
    expect(safe.signature).toBe("***REDACTED***");
    expect(safe.secret_ref).toBe("***REDACTED***");
  });
});
