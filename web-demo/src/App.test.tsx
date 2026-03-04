import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test } from "vitest";
import { App } from "./App";
import { SkillApiError, type SkillDemoApi } from "./lib/api";
import type {
  HistoryItem,
  SendbackResponse,
  SessionHistoryResponse,
  TurnAcceptedResponse
} from "./lib/types";

class FakeSkillDemoApi implements SkillDemoApi {
  private readonly historyBySession = new Map<string, HistoryItem[]>();
  private readonly counterBySession = new Map<string, number>();
  private readonly failFirstSendback: boolean;
  private sendbackAttempts = 0;

  constructor(options?: { failFirstSendback?: boolean }) {
    this.failFirstSendback = options?.failFirstSendback ?? false;
  }

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
    this.sendbackAttempts += 1;
    if (this.failFirstSendback && this.sendbackAttempts === 1) {
      throw new SkillApiError("IM_CHANNEL_UNAVAILABLE", "IM channel is unavailable. Please retry.", 502);
    }
    return {
      request_id: `sendback-${this.sendbackAttempts}`,
      session_id: input.sessionId,
      turn_id: input.turnId,
      trace_id: input.traceId,
      send_status: "sent",
      im_message_id: `im-msg-${this.sendbackAttempts}`,
      sent_at: new Date().toISOString()
    };
  }
}

describe("Phase 4/5 web demo flow", () => {
  test("typing slash at first position opens SKILL selector", async () => {
    const user = userEvent.setup();
    render(<App api={new FakeSkillDemoApi()} pollIntervalMs={20} />);

    await user.type(screen.getByTestId("chat-input"), "/");

    expect(screen.getByRole("dialog", { name: "skill-selector" })).toBeInTheDocument();
    expect(screen.getByText("SKILL")).toBeInTheDocument();
    expect(screen.getByText("Local OpenCode")).toBeInTheDocument();
  });

  test("generate inserts status card and reaches completed state", async () => {
    const user = userEvent.setup();
    render(<App api={new FakeSkillDemoApi()} pollIntervalMs={20} />);

    await user.type(screen.getByTestId("chat-input"), "/");
    await user.type(screen.getByTestId("slash-question"), "how are you?");
    await user.click(screen.getByTestId("generate-btn"));

    await waitFor(() => {
      expect(screen.getByText("completed")).toBeInTheDocument();
      expect(screen.getByText("Final answer how are you?")).toBeInTheDocument();
    });
  });

  test("expand supports follow-up and syncs summary back to card", async () => {
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

  test("full sendback uses preview-confirm and returns IM success banner", async () => {
    const user = userEvent.setup();
    render(<App api={new FakeSkillDemoApi()} pollIntervalMs={20} />);

    await user.type(screen.getByTestId("chat-input"), "/");
    await user.type(screen.getByTestId("slash-question"), "phase5");
    await user.click(screen.getByTestId("generate-btn"));
    await waitFor(() => expect(screen.getByText("Expand")).toBeInTheDocument());
    await user.click(screen.getByText("Expand"));

    await waitFor(() => {
      expect(screen.getAllByText("Send full to IM").length).toBeGreaterThan(0);
    });
    await user.click(screen.getAllByText("Send full to IM")[0]);

    expect(screen.getByTestId("sendback-preview")).toBeInTheDocument();
    await user.clear(screen.getByTestId("sendback-edit"));
    await user.type(screen.getByTestId("sendback-edit"), "edited for IM");
    await user.click(screen.getByTestId("sendback-confirm-btn"));

    await waitFor(() => {
      expect(screen.getByTestId("sendback-banner")).toHaveTextContent("Sent to IM");
    });
  });

  test("partial selection sendback keeps failed draft and supports one-click retry", async () => {
    const user = userEvent.setup();
    render(<App api={new FakeSkillDemoApi({ failFirstSendback: true })} pollIntervalMs={20} />);

    await user.type(screen.getByTestId("chat-input"), "/");
    await user.type(screen.getByTestId("slash-question"), "retry");
    await user.click(screen.getByTestId("generate-btn"));
    await waitFor(() => expect(screen.getByText("Expand")).toBeInTheDocument());
    await user.click(screen.getByText("Expand"));

    await waitFor(() => {
      expect(screen.getAllByDisplayValue("Final answer retry").length).toBeGreaterThan(0);
    });
    const snapshotBox = screen.getAllByDisplayValue("Final answer retry")[0] as HTMLTextAreaElement;
    snapshotBox.focus();
    snapshotBox.setSelectionRange(6, 12);
    fireEvent.select(snapshotBox);

    const partialButtons = screen.getAllByText("Use selected text");
    const enabledPartial = partialButtons.find((button) => !(button as HTMLButtonElement).disabled);
    expect(enabledPartial).toBeDefined();
    await user.click(enabledPartial as HTMLButtonElement);
    expect(screen.getByTestId("sendback-edit")).toHaveValue("answer");

    await user.click(screen.getByTestId("sendback-confirm-btn"));
    await waitFor(() => {
      expect(screen.getByTestId("sendback-banner")).toHaveTextContent("IM_CHANNEL_UNAVAILABLE");
      expect(screen.getByTestId("sendback-inline-error")).toBeInTheDocument();
      expect(screen.getByTestId("sendback-retry-btn")).toBeInTheDocument();
    });

    await user.click(screen.getByTestId("sendback-retry-btn"));
    await waitFor(() => {
      expect(screen.getByTestId("sendback-banner")).toHaveTextContent("Sent to IM");
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
