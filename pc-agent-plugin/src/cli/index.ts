import { pathToFileURL } from "node:url";
import { runSession, type CliLogger } from "./commands/runSession";

const HELP_TEXT = `pc-agent-plugin CLI

Usage:
  npm run cli -- run-session --question "<text>"

Environment:
  CHATCUI_TENANT_ID
  CHATCUI_CLIENT_ID
  CHATCUI_AK
  CHATCUI_SECRET_REF
  CHATCUI_CREDENTIAL_STATE
  CHATCUI_REAL_CHAIN=true
  CHATCUI_OPENCODE_ENDPOINT
  CHATCUI_GATEWAY_ENDPOINT
`;

interface ParsedArgs {
  command: "help" | "run-session";
  question: string;
}

export async function main(
  argv: string[] = process.argv.slice(2),
  logger: CliLogger = createConsoleLogger()
): Promise<number> {
  const parsed = parseArgs(argv);
  if (parsed.command === "help") {
    logger.info(HELP_TEXT);
    return 0;
  }

  const result = await runSession({
    question: parsed.question,
    logger
  });

  if (result.ok) {
    logger.info(
      `Session complete. session_id=${result.sessionId} trace_id=${result.traceId}`
    );
    return 0;
  }

  logger.error(`Session failed with ${result.error.error_code}`);
  return 1;
}

function parseArgs(argv: string[]): ParsedArgs {
  if (argv.length === 0 || argv.includes("--help") || argv.includes("-h")) {
    return { command: "help", question: "" };
  }

  const [command, ...rest] = argv;
  if (command !== "run-session") {
    return { command: "help", question: "" };
  }

  const questionArg = extractFlag(rest, "--question");
  return {
    command: "run-session",
    question: questionArg ?? "hello from cli"
  };
}

function extractFlag(args: string[], flagName: string): string | undefined {
  const idx = args.indexOf(flagName);
  if (idx === -1) {
    return undefined;
  }
  return args[idx + 1];
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

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().then((code) => {
    process.exitCode = code;
  });
}
