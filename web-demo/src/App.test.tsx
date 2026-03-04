import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, test, expect } from "vitest";
import { App } from "./App";
import type { SkillDemoApi } from "./lib/api";
import type { HistoryItem, SessionHistoryResponse, TurnAcceptedResponse } from "./lib/types";

class FakeSkillDemoApi implements SkillDemoApi {
  private readonly historyBySession = new Map<string, HistoryItem[]>();
  private readonly counterBySession = new Map<string, number>();

  async startTurn(input: {
    tenantId: string;
    clientId: string;
    sessionId: string;
    prompt: string;
  }): Promise<TurnAcceptedResponse> {
    const next = (this.counterBySession.get(input.sessionId) ?? 0) + 1;
    this.counterBySession.set(input.sessionId, next);
    const base = (next - 1) * 3;
    this.historyBySession.set(input.sessionId, [
      ...(this.historyBySession.get(input.sessionId) ?? []),
      historyItem(input.sessionId, `turn-${next}`, base + 1, "in_progress", "pending", `Thinking about ${input.prompt}`),
      historyItem(input.sessionId, `turn-${next}`, base + 2, "in_progress", "pending", `Draft answer ${input.prompt}`),
      historyItem(input.sessionId, `turn-${next}`, base + 3, "completed", "delivered", `Final answer ${input.prompt}`)
    ]);

    return {
      session_id: input.sessionId,
      turn_id: `turn-${next}`,
      receive_state: "accepted",
      accepted_at: new Date().toISOString()
    };
  }

  async getHistory(input: {
    tenantId: string;
    clientId: string;
    sessionId: string;
    limit?: number;
  }): Promise<SessionHistoryResponse> {
    return {
      session_id: input.sessionId,
      next_cursor: null,
      has_more: false,
      items: this.historyBySession.get(input.sessionId) ?? []
    };
  }
}

describe("Phase 4 web demo flow", () => {
  test("typing slash at first position opens SKILL selector", async () => {
    const user = userEvent.setup();
    render(<App api={new FakeSkillDemoApi()} pollIntervalMs={20} />);

    await user.type(screen.getByTestId("chat-input"), "/");

    expect(screen.getByRole("dialog", { name: "skill-selector" })).toBeInTheDocument();
    expect(screen.getByText("SKILL")).toBeInTheDocument();
    expect(screen.getByText("Local OpenCode")).toBeInTheDocument();
  });

  test("generate inserts one-line status card and marks session running/completed", async () => {
    const user = userEvent.setup();
    render(<App api={new FakeSkillDemoApi()} pollIntervalMs={20} />);

    await user.type(screen.getByTestId("chat-input"), "/");
    await user.type(screen.getByTestId("slash-question"), "how are you?");
    await user.click(screen.getByTestId("generate-btn"));

    expect(screen.getByText("Local OpenCode")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("completed")).toBeInTheDocument();
      expect(screen.getByText("Final answer how are you?")).toBeInTheDocument();
    });
  });

  test("expand view supports follow-up and syncs summary back to card", async () => {
    const user = userEvent.setup();
    render(<App api={new FakeSkillDemoApi()} pollIntervalMs={20} />);

    await user.type(screen.getByTestId("chat-input"), "/");
    await user.type(screen.getByTestId("slash-question"), "first");
    await user.click(screen.getByTestId("generate-btn"));

    await waitFor(() => {
      expect(screen.getByText("Expand")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Expand"));

    expect(screen.getByTestId("skill-overlay")).toBeInTheDocument();
    await user.type(screen.getByTestId("overlay-input"), "follow up");
    await user.click(screen.getByTestId("overlay-send-btn"));

    await waitFor(() => {
      expect(screen.getAllByText("Final answer follow up").length).toBeGreaterThan(0);
    });

    await user.click(screen.getByText("Back to chat"));
    await waitFor(() => {
      expect(screen.getAllByText("Final answer follow up").length).toBeGreaterThan(0);
    });
  });
});

function historyItem(
  sessionId: string,
  turnId: string,
  seq: number,
  turnStatus: string,
  deliveryStatus: string,
  snapshot: string
): HistoryItem {
  return {
    turn_id: turnId,
    seq,
    trace_id: `trace-${sessionId}-${seq}`,
    actor: "assistant",
    snapshot,
    turn_status: turnStatus,
    delivery_status: deliveryStatus,
    created_at: new Date().toISOString()
  };
}
