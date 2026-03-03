import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const pluginRoot = resolve(fileURLToPath(new URL("..", import.meta.url)));
const adapterDirs = [
  join(pluginRoot, "src", "host-adapter"),
  join(pluginRoot, "src", "cli")
];

const forbiddenPatterns = [
  { regex: /implements\s+ProtocolBridge/, reason: "Adapter implements ProtocolBridge directly" },
  { regex: /\btoGateway\s*\(/, reason: "Adapter defines toGateway conversion" },
  { regex: /\bfromGateway\s*\(/, reason: "Adapter defines fromGateway conversion" },
  { regex: /createHmac\s*\(/, reason: "Adapter owns signing implementation" }
];

const requiredFactoryUsage = [
  join(pluginRoot, "src", "host-adapter", "HostPluginAdapter.ts"),
  join(pluginRoot, "src", "cli", "runtime", "CliRuntimeBootstrap.ts")
];

const violations = [];

for (const dir of adapterDirs) {
  for (const file of readTsFiles(dir)) {
    const content = readFileSync(file, "utf8");
    for (const pattern of forbiddenPatterns) {
      if (pattern.regex.test(content)) {
        violations.push(`${toRelative(file)}: ${pattern.reason}`);
      }
    }
  }
}

for (const file of requiredFactoryUsage) {
  if (!existsSync(file)) {
    violations.push(`${toRelative(file)}: required factory file is missing`);
    continue;
  }
  const content = readFileSync(file, "utf8");
  if (!content.includes("createBridgeRuntime")) {
    violations.push(`${toRelative(file)}: must use createBridgeRuntime`);
  }
}

if (violations.length > 0) {
  // eslint-disable-next-line no-console
  console.error("No-drift validation failed:");
  for (const violation of violations) {
    // eslint-disable-next-line no-console
    console.error(`- ${violation}`);
  }
  process.exitCode = 1;
} else {
  // eslint-disable-next-line no-console
  console.log("No-drift validation passed.");
}

function readTsFiles(dir) {
  if (!existsSync(dir)) {
    return [];
  }
  const files = [];
  for (const entry of readdirSync(dir)) {
    const entryPath = join(dir, entry);
    const stats = statSync(entryPath);
    if (stats.isDirectory()) {
      files.push(...readTsFiles(entryPath));
      continue;
    }
    if (entry.endsWith(".ts")) {
      files.push(entryPath);
    }
  }
  return files;
}

function toRelative(path) {
  return relative(pluginRoot, path);
}
