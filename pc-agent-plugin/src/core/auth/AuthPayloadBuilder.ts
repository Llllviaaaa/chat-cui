export interface AuthPayloadFields {
  ak: string;
  tenant_id: string;
  client_id: string;
  timestamp: number;
  nonce: string;
  session_id: string;
}

export const AUTH_PAYLOAD_ORDER: ReadonlyArray<keyof AuthPayloadFields> = [
  "ak",
  "tenant_id",
  "client_id",
  "timestamp",
  "nonce",
  "session_id"
];

function normalize(value: unknown, key: string): string {
  if (typeof value === "number") {
    return String(value);
  }
  if (typeof value !== "string") {
    throw new Error(`Missing required field: ${key}`);
  }
  const trimmed = value.trim();
  if (!trimmed) {
    throw new Error(`Missing required field: ${key}`);
  }
  return trimmed;
}

export function buildCanonicalPayload(fields: AuthPayloadFields): string {
  const lines = AUTH_PAYLOAD_ORDER.map((key) => `${key}:${normalize(fields[key], key)}`);
  return lines.join("\n");
}
