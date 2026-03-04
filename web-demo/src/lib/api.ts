import type { SessionHistoryResponse, TurnAcceptedResponse } from "./types";

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
      const text = await response.text();
      throw new Error(`startTurn failed: ${response.status} ${text}`);
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
      const text = await response.text();
      throw new Error(`getHistory failed: ${response.status} ${text}`);
    }
    return (await response.json()) as SessionHistoryResponse;
  }
}
