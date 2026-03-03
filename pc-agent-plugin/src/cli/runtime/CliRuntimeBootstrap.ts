import { randomUUID } from "node:crypto";
import { AuthSigner } from "../../core/auth/AuthSigner";
import type { BridgeRuntime } from "../../core/runtime/BridgeRuntime";
import { createBridgeRuntime } from "../../core/runtime/BridgeRuntimeFactory";
import type { CredentialProvider } from "../../core/security/CredentialProvider";
import { WindowsCredentialProvider } from "../../core/security/WindowsCredentialProvider";
import {
  CliAuthConfigError,
  CliAuthConfigLoader,
  type CliAuthConfig
} from "../config/CliAuthConfigLoader";

export type CliBootstrapErrorCode =
  | "AUTH_CONFIG_INVALID"
  | "REAL_CHAIN_DISABLED"
  | "MISSING_REAL_CHAIN_CONFIG";

export class CliRuntimeBootstrapError extends Error {
  readonly code: CliBootstrapErrorCode;

  constructor(code: CliBootstrapErrorCode, message: string) {
    super(message);
    this.code = code;
  }
}

export interface CliRuntimeChainConfig {
  realChain: boolean;
  openCodeEndpoint: string;
  gatewayEndpoint: string;
}

export interface CliAuthHandshake {
  tenantId: string;
  clientId: string;
  ak: string;
  sessionId: string;
  nonce: string;
  timestamp: number;
  traceId: string;
  signature: string;
}

export interface CliRuntimeBootstrapResult {
  runtime: BridgeRuntime;
  auth: CliAuthHandshake;
  chain: CliRuntimeChainConfig;
  warnings: string[];
}

export interface CliRuntimeBootstrapOptions {
  env?: NodeJS.ProcessEnv;
  configLoader?: CliAuthConfigLoader;
  credentialProvider?: CredentialProvider;
  signer?: AuthSigner;
  runtimeFactory?: () => BridgeRuntime;
  nonceFactory?: () => string;
  sessionIdFactory?: () => string;
  traceIdFactory?: () => string;
  clock?: () => number;
}

export async function bootstrapRuntime(
  options: CliRuntimeBootstrapOptions = {}
): Promise<CliRuntimeBootstrapResult> {
  const env = options.env ?? process.env;
  const configLoader = options.configLoader ?? new CliAuthConfigLoader();
  const signer = options.signer ?? new AuthSigner();
  const credentialProvider =
    options.credentialProvider ?? new WindowsCredentialProvider();
  const runtimeFactory = options.runtimeFactory ?? (() => createBridgeRuntime());
  const nonceFactory = options.nonceFactory ?? (() => randomUUID());
  const sessionIdFactory = options.sessionIdFactory ?? (() => randomUUID());
  const traceIdFactory = options.traceIdFactory ?? (() => `trace-${randomUUID()}`);
  const clock = options.clock ?? (() => Math.floor(Date.now() / 1000));

  let authConfig: CliAuthConfig;
  try {
    authConfig = configLoader.fromEnvironment(env);
  } catch (error) {
    const message =
      error instanceof CliAuthConfigError ? error.message : "Invalid auth configuration";
    throw new CliRuntimeBootstrapError("AUTH_CONFIG_INVALID", message);
  }

  const chain = loadRealChainConfig(env);
  if (!chain.realChain) {
    throw new CliRuntimeBootstrapError(
      "REAL_CHAIN_DISABLED",
      "CHATCUI_REAL_CHAIN must be true for run-session."
    );
  }

  const secret = await credentialProvider.readSecret(authConfig.secretRef);
  const sessionId = env.CHATCUI_SESSION_ID?.trim() || sessionIdFactory();
  const nonce = env.CHATCUI_NONCE?.trim() || nonceFactory();
  const timestamp = clock();
  const traceId = env.CHATCUI_TRACE_ID?.trim() || traceIdFactory();

  const signature = signer.sign(
    {
      ak: authConfig.ak,
      tenant_id: authConfig.tenantId,
      client_id: authConfig.clientId,
      timestamp,
      nonce,
      session_id: sessionId
    },
    secret
  );

  const runtime = runtimeFactory();
  const warnings = authConfig.warning ? [authConfig.warning] : [];

  return {
    runtime,
    auth: {
      tenantId: authConfig.tenantId,
      clientId: authConfig.clientId,
      ak: authConfig.ak,
      sessionId,
      nonce,
      timestamp,
      traceId,
      signature
    },
    chain,
    warnings
  };
}

function loadRealChainConfig(env: NodeJS.ProcessEnv): CliRuntimeChainConfig {
  const realChain = (env.CHATCUI_REAL_CHAIN ?? "false").trim().toLowerCase() === "true";
  const openCodeEndpoint = env.CHATCUI_OPENCODE_ENDPOINT?.trim() ?? "";
  const gatewayEndpoint = env.CHATCUI_GATEWAY_ENDPOINT?.trim() ?? "";

  if (realChain && (!openCodeEndpoint || !gatewayEndpoint)) {
    throw new CliRuntimeBootstrapError(
      "MISSING_REAL_CHAIN_CONFIG",
      "CHATCUI_OPENCODE_ENDPOINT and CHATCUI_GATEWAY_ENDPOINT are required for real chain."
    );
  }

  return {
    realChain,
    openCodeEndpoint,
    gatewayEndpoint
  };
}
