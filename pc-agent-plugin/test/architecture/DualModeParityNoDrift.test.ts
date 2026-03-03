import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { bootstrapRuntime } from "../../src/cli/runtime/CliRuntimeBootstrap";
import { HostPluginAdapter } from "../../src/host-adapter/HostPluginAdapter";

class StubCredentialProvider {
  constructor(private readonly secret: string) {}

  async readSecret(): Promise<string> {
    return this.secret;
  }

  async upsertSecret(): Promise<void> {
    // no-op
  }

  async deleteSecret(): Promise<void> {
    // no-op
  }
}

describe("DualModeParityNoDrift", () => {
  it("instantiates shared-core runtime in both plugin mode and CLI mode", async () => {
    const hostAdapter = new HostPluginAdapter({
      emitHostEvent: () => {
        // no-op in parity test
      }
    });
    hostAdapter.init();
    hostAdapter.start();
    expect(hostAdapter.runtimeSource()).toBe("shared-core");
    hostAdapter.stop();
    hostAdapter.dispose();

    const cliBootstrap = await bootstrapRuntime({
      env: {
        CHATCUI_TENANT_ID: "tenant-a",
        CHATCUI_CLIENT_ID: "client-a",
        CHATCUI_AK: "ak_live_1234",
        CHATCUI_SECRET_REF: "wincred://tenant-a/client-a",
        CHATCUI_REAL_CHAIN: "true",
        CHATCUI_OPENCODE_ENDPOINT: "http://127.0.0.1:11400",
        CHATCUI_GATEWAY_ENDPOINT: "ws://127.0.0.1:8080/ws"
      },
      credentialProvider: new StubCredentialProvider("secret-a")
    });

    expect(cliBootstrap.runtime.source).toBe("shared-core");
  });

  it("keeps both adapter entrypoints bound to createBridgeRuntime", () => {
    const pluginRoot = resolvePluginRoot();
    const root = join(pluginRoot, "src");
    const hostAdapterContent = readFileSync(
      join(root, "host-adapter", "HostPluginAdapter.ts"),
      "utf8"
    );
    const cliBootstrapContent = readFileSync(
      join(root, "cli", "runtime", "CliRuntimeBootstrap.ts"),
      "utf8"
    );

    expect(hostAdapterContent).toContain("createBridgeRuntime");
    expect(cliBootstrapContent).toContain("createBridgeRuntime");
  });
});

function resolvePluginRoot(): string {
  const nested = join(process.cwd(), "pc-agent-plugin");
  return existsSync(nested) ? nested : process.cwd();
}
