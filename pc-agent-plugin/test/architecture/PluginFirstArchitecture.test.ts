import { readFileSync, readdirSync, existsSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { createBridgeRuntime } from "../../src/core/runtime/BridgeRuntimeFactory";

function readAllTsFiles(dir: string): string[] {
  if (!existsSync(dir)) {
    return [];
  }
  const output: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      output.push(...readAllTsFiles(path));
    } else if (entry.name.endsWith(".ts")) {
      output.push(path);
    }
  }
  return output;
}

describe("PluginFirstArchitecture", () => {
  it("creates runtime from shared factory", () => {
    const runtime = createBridgeRuntime();
    expect(runtime.source).toBe("shared-core");
  });

  it("keeps protocol conversion ownership outside adapters", () => {
    const adapterRoots = [
      join(process.cwd(), "pc-agent-plugin", "src", "host-adapter"),
      join(process.cwd(), "pc-agent-plugin", "src", "cli")
    ];

    for (const root of adapterRoots) {
      for (const file of readAllTsFiles(root)) {
        const content = readFileSync(file, "utf8");
        expect(content).not.toMatch(/implements\s+ProtocolBridge/);
        expect(content).not.toMatch(/toGateway\s*\(/);
        expect(content).not.toMatch(/fromGateway\s*\(/);
      }
    }
  });
});
