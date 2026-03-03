import { randomUUID } from "node:crypto";
import {
  mapAuthFailure,
  type AuthErrorEnvelope,
  type AuthFailureCode
} from "../../core/auth/AuthFailureMapper";
import { OPCODE_TYPES } from "../../core/bridge/ProtocolBridge";
import {
  bootstrapRuntime,
  CliRuntimeBootstrapError,
  type CliRuntimeBootstrapOptions
} from "../runtime/CliRuntimeBootstrap";

export interface CliLogger {
  info(message: string): void;
  error(message: string): void;
}

export interface RunSessionOptions {
  question: string;
  env?: NodeJS.ProcessEnv;
  logger?: CliLogger;
  bootstrapOptions?: Omit<CliRuntimeBootstrapOptions, "env">;
}

export interface RunSessionSuccess {
  ok: true;
  sessionId: string;
  traceId: string;
  markers: string[];
}

export interface RunSessionFailure {
  ok: false;
  error: AuthErrorEnvelope;
  markers: string[];
}

export type RunSessionResult = RunSessionSuccess | RunSessionFailure;

export async function runSession(options: RunSessionOptions): Promise<RunSessionResult> {
  const logger = options.logger ?? createConsoleLogger();
  const env = options.env ?? process.env;
  const traceId = env.CHATCUI_TRACE_ID?.trim() || `trace-${randomUUID()}`;
  const sessionId = env.CHATCUI_SESSION_ID?.trim() || `session-${randomUUID()}`;
  const debugId = env.CHATCUI_DEBUG_ID?.trim() || `debug-${randomUUID()}`;

  try {
    const bootstrap = await bootstrapRuntime({
      env,
      ...(options.bootstrapOptions ?? {})
    });
    const runtime = bootstrap.runtime;
    const unsubscribe = runtime.subscribe((event) => {
      logger.info(`[plugin-event] ${event.name}`);
    });

    runtime.init();
    runtime.start();
    runtime.startSession(bootstrap.auth.sessionId, bootstrap.auth.traceId);
    runtime.sendOpenCode({
      type: OPCODE_TYPES.TURN_REQUEST,
      payload: {
        session_id: bootstrap.auth.sessionId,
        turn_id: `turn-${randomUUID()}`,
        seq: 1,
        trace_id: bootstrap.auth.traceId,
        prompt: options.question
      }
    });
    runtime.reportHealth("healthy", "real chain configuration validated");

    logger.info(
      [
        "Real chain session started.",
        `OpenCode: ${bootstrap.chain.openCodeEndpoint}`,
        `AI-Gateway: ${bootstrap.chain.gatewayEndpoint}`,
        `Session: ${bootstrap.auth.sessionId}`,
        `Trace: ${bootstrap.auth.traceId}`
      ].join(" ")
    );

    runtime.endSession("cli_run_complete");
    runtime.stop();
    runtime.dispose();
    unsubscribe();

    return {
      ok: true,
      sessionId: bootstrap.auth.sessionId,
      traceId: bootstrap.auth.traceId,
      markers: [
        "plugin_mode_shared_core",
        "cli_mode_shared_core",
        "real_chain_session_started",
        "auth_v1_signature_generated"
      ]
    };
  } catch (error) {
    const envelope = mapFailure(error, traceId, sessionId, debugId);
    logger.error(JSON.stringify(envelope));
    return {
      ok: false,
      error: envelope,
      markers: ["real_chain_rejected", "auth_v1_failure_signal"]
    };
  }
}

function mapFailure(
  error: unknown,
  traceId: string,
  sessionId: string,
  debugId: string
): AuthErrorEnvelope {
  let code: AuthFailureCode = "AUTH_V1_INVALID_SIGNATURE";

  if (error instanceof CliRuntimeBootstrapError) {
    switch (error.code) {
      case "AUTH_CONFIG_INVALID":
        code = "AUTH_V1_MISSING_CREDENTIAL";
        break;
      case "MISSING_REAL_CHAIN_CONFIG":
        code = "AUTH_V1_MISSING_CREDENTIAL";
        break;
      case "REAL_CHAIN_DISABLED":
        code = "AUTH_V1_PERMISSION_DENIED";
        break;
      default:
        code = "AUTH_V1_INVALID_SIGNATURE";
    }
  }

  return mapAuthFailure(code, traceId, sessionId, debugId);
}

function createConsoleLogger(): CliLogger {
  return {
    info(message: string): void {
      // eslint-disable-next-line no-console
      console.log(message);
    },
    error(message: string): void {
      // eslint-disable-next-line no-console
      console.error(message);
    }
  };
}
