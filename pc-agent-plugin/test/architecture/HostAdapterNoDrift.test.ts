import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

function readAllTsFiles(dir: string): string[] {
  if (!existsSync(dir)) {
    return [];
  }
  const files: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const entryPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...readAllTsFiles(entryPath));
      continue;
    }
    if (entry.name.endsWith(".ts")) {
      files.push(entryPath);
    }
  }
  return files;
}

function resolvePluginRoot(): string {
  const nested = join(process.cwd(), "pc-agent-plugin");
  return existsSync(nested) ? nested : process.cwd();
}

describe("HostAdapterNoDrift", () => {
  it("keeps host adapter as a thin layer over shared runtime", () => {
    const adapterRoot = join(resolvePluginRoot(), "src", "host-adapter");
    const files = readAllTsFiles(adapterRoot);

    expect(files.length).toBeGreaterThan(0);

    for (const file of files) {
      const content = readFileSync(file, "utf8");
      expect(content).not.toMatch(/implements\s+ProtocolBridge/);
      expect(content).not.toMatch(/\btoGateway\s*\(/);
      expect(content).not.toMatch(/\bfromGateway\s*\(/);
      expect(content).not.toMatch(/new\s+AuthSigner/);
      expect(content).not.toMatch(/createHmac\s*\(/);
    }
  });

  it("routes runtime creation through shared factory", () => {
    const adapterFile = join(
      resolvePluginRoot(),
      "src",
      "host-adapter",
      "HostPluginAdapter.ts"
    );
    const content = readFileSync(adapterFile, "utf8");
    expect(content).toContain("createBridgeRuntime");
  });
});
