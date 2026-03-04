import type {
  SendbackResponse,
  SessionHistoryResponse,
  TurnAcceptedResponse
} from "./types";

export class SkillApiError extends Error {
  readonly code: string;
  readonly status: number;

  constructor(code: string, message: string, status: number) {
    super(message);
    this.name = "SkillApiError";
    this.code = code;
    this.status = status;
  }
}

type ErrorEnvelope = {
  error?: {
    code?: string;
    message?: string;
  };
};

export interface SkillDemoApi {
  startTurn(input: {
    tenantId: string;
    clientId: string;
    sessionId: string;
    prompt: string;
  }): Promise<TurnAcceptedResponse>;
  getHistory(input: {
    tenantId: string;
    clientId: string;
    sessionId: string;
    limit?: number;
  }): Promise<SessionHistoryResponse>;
  sendback(input: {
    tenantId: string;
    clientId: string;
    sessionId: string;
    turnId: string;
    traceId: string;
    conversationId: string;
    selectedText: string;
    messageText: string;
  }): Promise<SendbackResponse>;
}

export class HttpSkillDemoApi implements SkillDemoApi {
  private readonly baseUrl: string;

  constructor(baseUrl = import.meta.env.VITE_SKILL_API_BASE ?? "http://localhost:8080") {
    this.baseUrl = baseUrl.replace(/\/+$/, "");
  }

  async startTurn(input: {
    tenantId: string;
    clientId: string;
    sessionId: string;
    prompt: string;
  }): Promise<TurnAcceptedResponse> {
    const response = await fetch(
      `${this.baseUrl}/demo/skill/sessions/${encodeURIComponent(input.sessionId)}/turns`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          tenant_id: input.tenantId,
          client_id: input.clientId,
          prompt: input.prompt
        })
      }
    );
    if (!response.ok) {
      throw await parseSkillError("START_TURN_FAILED", response);
    }
    return (await response.json()) as TurnAcceptedResponse;
  }

  async getHistory(input: {
    tenantId: string;
    clientId: string;
    sessionId: string;
    limit?: number;
  }): Promise<SessionHistoryResponse> {
    const params = new URLSearchParams({
      tenant_id: input.tenantId,
      client_id: input.clientId,
      limit: String(input.limit ?? 50)
    });
    const response = await fetch(
      `${this.baseUrl}/sessions/${encodeURIComponent(input.sessionId)}/history?${params.toString()}`
    );
    if (!response.ok) {
      throw await parseSkillError("GET_HISTORY_FAILED", response);
    }
    return (await response.json()) as SessionHistoryResponse;
  }

  async sendback(input: {
    tenantId: string;
    clientId: string;
    sessionId: string;
    turnId: string;
    traceId: string;
    conversationId: string;
    selectedText: string;
    messageText: string;
  }): Promise<SendbackResponse> {
    const response = await fetch(
      `${this.baseUrl}/sessions/${encodeURIComponent(input.sessionId)}/sendback`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          tenant_id: input.tenantId,
          client_id: input.clientId,
          turn_id: input.turnId,
          trace_id: input.traceId,
          conversation_id: input.conversationId,
          selected_text: input.selectedText,
          message_text: input.messageText
        })
      }
    );
    if (!response.ok) {
      throw await parseSkillError("SEND_TO_IM_FAILED", response);
    }
    return (await response.json()) as SendbackResponse;
  }
}

async function parseSkillError(fallbackCode: string, response: Response): Promise<SkillApiError> {
  let body: ErrorEnvelope | null = null;
  let rawText = "";
  try {
    body = (await response.json()) as ErrorEnvelope;
  } catch {
    rawText = await response.text();
  }
  const code = body?.error?.code ?? fallbackCode;
  const message = body?.error?.message ?? (rawText || `HTTP ${response.status}`);
  return new SkillApiError(code, message, response.status);
}
