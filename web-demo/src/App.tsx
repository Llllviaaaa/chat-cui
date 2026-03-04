import { useEffect, useMemo, useRef, useState } from "react";
import { HttpSkillDemoApi, SkillApiError, type SkillDemoApi } from "./lib/api";
import type { CardStatus, HistoryItem, SkillSessionViewModel } from "./lib/types";

type AppProps = {
  api?: SkillDemoApi;
  tenantId?: string;
  clientId?: string;
  pollIntervalMs?: number;
};

type SendbackDraft = {
  sessionId: string;
  turnId: string;
  traceId: string;
  selectedText: string;
  messageText: string;
  mode: "full" | "partial";
};

type OverlayNotice = {
  kind: "success" | "error";
  code?: string;
  message: string;
};

const SKILL_LABEL = "Local OpenCode";

export function App({
  api = new HttpSkillDemoApi(),
  tenantId = "tenant-demo",
  clientId = "client-demo",
  pollIntervalMs = 350
}: AppProps) {
  const [chatInput, setChatInput] = useState("");
  const [slashQuestion, setSlashQuestion] = useState("");
  const [sessionsById, setSessionsById] = useState<Record<string, SkillSessionViewModel>>({});
  const [sessionOrder, setSessionOrder] = useState<string[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [overlayOpen, setOverlayOpen] = useState(false);
  const [overlayInput, setOverlayInput] = useState("");
  const [conflictSessionId, setConflictSessionId] = useState<string | null>(null);
  const [selectedSnippetsByItem, setSelectedSnippetsByItem] = useState<Record<string, string>>({});
  const [sendbackDraft, setSendbackDraft] = useState<SendbackDraft | null>(null);
  const [sendbackSubmitting, setSendbackSubmitting] = useState(false);
  const [overlayNotice, setOverlayNotice] = useState<OverlayNotice | null>(null);
  const pollingTimersRef = useRef<Map<string, number>>(new Map());
  const historyScrollRef = useRef<Map<string, number>>(new Map());
  const overlayHistoryRef = useRef<HTMLDivElement | null>(null);

  const slashVisible = chatInput.startsWith("/");
  const activeSession = activeSessionId ? sessionsById[activeSessionId] : undefined;
  const activeHistorySize = activeSession?.history.length ?? 0;

  const runningSessionId = useMemo(() => {
    const current = sessionOrder.find((sessionId) => sessionsById[sessionId]?.status === "running");
    return current ?? null;
  }, [sessionOrder, sessionsById]);

  useEffect(() => {
    return () => {
      for (const timer of pollingTimersRef.current.values()) {
        window.clearInterval(timer);
      }
      pollingTimersRef.current.clear();
    };
  }, []);

  useEffect(() => {
    if (!overlayOpen || !activeSessionId || !overlayHistoryRef.current) {
      return;
    }
    const saved = historyScrollRef.current.get(activeSessionId);
    overlayHistoryRef.current.scrollTop =
      saved ?? overlayHistoryRef.current.scrollHeight;
  }, [overlayOpen, activeSessionId, activeHistorySize]);

  const startPolling = (sessionId: string) => {
    if (pollingTimersRef.current.has(sessionId)) {
      return;
    }

    const tick = async () => {
      try {
        const history = await api.getHistory({ tenantId, clientId, sessionId, limit: 50 });
        setSessionsById((previous) => {
          const current = previous[sessionId];
          if (!current) {
            return previous;
          }

          const status = resolveCardStatus(history.items);
          const summary = resolveSummary(history.items, status);
          const next = {
            ...current,
            history: history.items,
            status,
            summary
          };
          return {
            ...previous,
            [sessionId]: next
          };
        });

        if (isTerminal(resolveCardStatus(history.items))) {
          stopPolling(sessionId);
        }
      } catch (error) {
        stopPolling(sessionId);
        const message = error instanceof Error ? error.message : "history refresh failed";
        setSessionsById((previous) => {
          const current = previous[sessionId];
          if (!current) {
            return previous;
          }
          return {
            ...previous,
            [sessionId]: {
              ...current,
              status: "failed",
              summary: `Failed: ${message}`
            }
          };
        });
      }
    };

    void tick();
    const timer = window.setInterval(() => {
      void tick();
    }, pollIntervalMs);
    pollingTimersRef.current.set(sessionId, timer);
  };

  const stopPolling = (sessionId: string) => {
    const timer = pollingTimersRef.current.get(sessionId);
    if (timer == null) {
      return;
    }
    window.clearInterval(timer);
    pollingTimersRef.current.delete(sessionId);
  };

  const ensureSession = (sessionId: string) => {
    setSessionsById((previous) => {
      if (previous[sessionId]) {
        return previous;
      }
      return {
        ...previous,
        [sessionId]: {
          sessionId,
          status: "waiting",
          summary: "Submitting request...",
          history: []
        }
      };
    });
    setSessionOrder((previous) => (previous.includes(sessionId) ? previous : [...previous, sessionId]));
  };

  const startTurnForSession = async (sessionId: string, prompt: string) => {
    ensureSession(sessionId);
    setSessionsById((previous) => ({
      ...previous,
      [sessionId]: {
        ...previous[sessionId],
        sessionId,
        status: "waiting",
        summary: "Submitting request...",
        history: previous[sessionId]?.history ?? []
      }
    }));

    try {
      await api.startTurn({ tenantId, clientId, sessionId, prompt });
      setSessionsById((previous) => {
        const current = previous[sessionId];
        if (!current) {
          return previous;
        }
        return {
          ...previous,
          [sessionId]: {
            ...current,
            status: "running",
            summary: "OpenCode is calling skill..."
          }
        };
      });
      startPolling(sessionId);
    } catch (error) {
      const message = error instanceof Error ? error.message : "unknown error";
      setSessionsById((previous) => {
        const current = previous[sessionId];
        if (!current) {
          return previous;
        }
        return {
          ...previous,
          [sessionId]: {
            ...current,
            status: "failed",
            summary: `Failed: ${message}`
          }
        };
      });
    }
  };

  const handleGenerate = async () => {
    const prompt = slashQuestion.trim();
    if (!prompt) {
      return;
    }

    if (runningSessionId) {
      setConflictSessionId(runningSessionId);
      return;
    }

    const sessionId = buildSessionId();
    setActiveSessionId(sessionId);
    setConflictSessionId(null);
    setSlashQuestion("");
    setChatInput("");
    await startTurnForSession(sessionId, prompt);
  };

  const handleOpenSession = (sessionId: string) => {
    setActiveSessionId(sessionId);
    setOverlayOpen(true);
    setConflictSessionId(null);
    setOverlayNotice(null);
    setSendbackDraft((previous) => (previous?.sessionId === sessionId ? previous : null));
  };

  const handleOverlayClose = () => {
    if (activeSessionId && overlayHistoryRef.current) {
      historyScrollRef.current.set(activeSessionId, overlayHistoryRef.current.scrollTop);
    }
    setOverlayOpen(false);
  };

  const handleOverlaySend = async () => {
    if (!activeSessionId) {
      return;
    }
    const prompt = overlayInput.trim();
    if (!prompt) {
      return;
    }
    setOverlayInput("");
    await startTurnForSession(activeSessionId, prompt);
  };

  const handleSnapshotSelection = (item: HistoryItem, snapshot: string, start: number, end: number) => {
    const key = buildItemKey(item);
    if (end <= start) {
      setSelectedSnippetsByItem((previous) => {
        if (!(key in previous)) {
          return previous;
        }
        const next = { ...previous };
        delete next[key];
        return next;
      });
      return;
    }
    const selected = snapshot.slice(start, end).trim();
    setSelectedSnippetsByItem((previous) => ({
      ...previous,
      [key]: selected
    }));
  };

  const openSendbackDraft = (
    item: HistoryItem,
    content: string,
    mode: "full" | "partial"
  ) => {
    if (!activeSessionId) {
      return;
    }
    const normalized = content.trim();
    if (!normalized) {
      return;
    }
    setOverlayNotice(null);
    setSendbackDraft({
      sessionId: activeSessionId,
      turnId: item.turn_id,
      traceId: item.trace_id,
      selectedText: normalized,
      messageText: normalized,
      mode
    });
  };

  const submitSendback = async () => {
    if (!sendbackDraft || sendbackSubmitting) {
      return;
    }
    setSendbackSubmitting(true);
    try {
      const response = await api.sendback({
        tenantId,
        clientId,
        sessionId: sendbackDraft.sessionId,
        turnId: sendbackDraft.turnId,
        traceId: sendbackDraft.traceId,
        conversationId: `im-conversation-${sendbackDraft.sessionId}`,
        selectedText: sendbackDraft.selectedText,
        messageText: sendbackDraft.messageText
      });
      setOverlayNotice({
        kind: "success",
        message: `Sent to IM (${response.im_message_id ?? response.request_id}).`
      });
      setSendbackDraft(null);
    } catch (error) {
      if (error instanceof SkillApiError) {
        setOverlayNotice({
          kind: "error",
          code: error.code,
          message: error.message
        });
      } else {
        setOverlayNotice({
          kind: "error",
          code: "SEND_TO_IM_FAILED",
          message: error instanceof Error ? error.message : "Sendback failed. Please retry."
        });
      }
    } finally {
      setSendbackSubmitting(false);
    }
  };

  return (
    <div className="app-shell">
      <header className="top-bar">
        <h1>Chat CUI Web Demo</h1>
        <p>Phase 5: select, preview, sendback to IM with retry</p>
      </header>

      <main className="chat-layout">
        <section className="chat-timeline">
          {sessionOrder.length === 0 && (
            <div className="empty-state">No skill sessions yet. Type "/" to start.</div>
          )}

          {sessionOrder.map((sessionId) => {
            const session = sessionsById[sessionId];
            if (!session) {
              return null;
            }
            return (
              <article key={sessionId} className="status-card" data-testid={`status-card-${sessionId}`}>
                <div className="status-line">
                  <span className="skill">{SKILL_LABEL}</span>
                  <span className={`badge badge-${session.status}`}>{session.status}</span>
                  <span className="summary" title={session.summary}>
                    {session.summary}
                  </span>
                </div>
                <div className="actions">
                  {(session.status === "running" || session.status === "completed") && (
                    <button
                      type="button"
                      onClick={() => handleOpenSession(sessionId)}
                      className="link-btn"
                    >
                      Expand
                    </button>
                  )}
                  {session.status === "failed" && (
                    <button
                      type="button"
                      onClick={() => handleOpenSession(sessionId)}
                      className="link-btn"
                    >
                      View details
                    </button>
                  )}
                </div>
              </article>
            );
          })}
        </section>

        <section className="chat-input-area">
          <label htmlFor="chat-input">Chat Input</label>
          <input
            id="chat-input"
            data-testid="chat-input"
            value={chatInput}
            onChange={(event) => setChatInput(event.target.value)}
            placeholder='Type "/" to open SKILL'
            disabled={overlayOpen}
          />

          {slashVisible && (
            <div className="slash-panel" role="dialog" aria-label="skill-selector">
              <div className="panel-title">SKILL</div>
              <button type="button" className="skill-option selected">
                {SKILL_LABEL}
              </button>
              <input
                data-testid="slash-question"
                value={slashQuestion}
                onChange={(event) => setSlashQuestion(event.target.value)}
                placeholder="Ask Local OpenCode..."
              />
              <button
                type="button"
                data-testid="generate-btn"
                onClick={() => {
                  void handleGenerate();
                }}
              >
                Generate
              </button>
            </div>
          )}

          {conflictSessionId && (
            <div className="notice" data-testid="active-session-notice">
              A skill session is already running.
              <button
                type="button"
                onClick={() => handleOpenSession(conflictSessionId)}
                className="link-btn"
              >
                Open current session
              </button>
            </div>
          )}
        </section>
      </main>

      {overlayOpen && activeSession && (
        <section className="overlay" data-testid="skill-overlay">
          <header className="overlay-header">
            <div>
              <h2>{SKILL_LABEL}</h2>
              <p>
                Session: {activeSession.sessionId} | Status: {activeSession.status}
              </p>
              {overlayNotice && (
                <p
                  className={`sendback-banner sendback-banner-${overlayNotice.kind}`}
                  data-testid="sendback-banner"
                >
                  {overlayNotice.code ? `[${overlayNotice.code}] ` : ""}
                  {overlayNotice.message}
                </p>
              )}
            </div>
            <button type="button" onClick={handleOverlayClose}>
              Back to chat
            </button>
          </header>

          <div className="overlay-history" ref={overlayHistoryRef}>
            {activeSession.history.length === 0 && (
              <div className="empty-state">Waiting for session history...</div>
            )}
            {activeSession.history.map((item) => {
              const itemKey = buildItemKey(item);
              const selectedSnippet = selectedSnippetsByItem[itemKey] ?? "";
              const assistantContent = item.actor === "assistant" && (item.snapshot?.trim().length ?? 0) > 0;
              return (
                <div key={itemKey} className="history-item">
                  <div className="meta">
                    <span>{item.turn_id}</span>
                    <span>{item.seq}</span>
                    <span>{item.turn_status}</span>
                    <span>{item.delivery_status}</span>
                  </div>
                  {assistantContent ? (
                    <textarea
                      className="snapshot-view"
                      value={item.snapshot}
                      readOnly
                      onSelect={(event) => {
                        const current = event.currentTarget;
                        handleSnapshotSelection(item, current.value, current.selectionStart, current.selectionEnd);
                      }}
                    />
                  ) : (
                    <p>{item.snapshot}</p>
                  )}
                  {assistantContent && (
                    <div className="sendback-actions">
                      <button
                        type="button"
                        className="secondary-btn"
                        onClick={() => openSendbackDraft(item, item.snapshot, "full")}
                      >
                        Send full to IM
                      </button>
                      <button
                        type="button"
                        className="secondary-btn"
                        disabled={!selectedSnippet}
                        onClick={() => openSendbackDraft(item, selectedSnippet, "partial")}
                      >
                        Use selected text
                      </button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {sendbackDraft && sendbackDraft.sessionId === activeSession.sessionId && (
            <section className="sendback-panel" data-testid="sendback-preview">
              <div className="sendback-title">
                Sendback Preview ({sendbackDraft.mode === "full" ? "full block" : "partial selection"})
              </div>
              <textarea
                data-testid="sendback-edit"
                value={sendbackDraft.messageText}
                onChange={(event) => {
                  const nextText = event.target.value;
                  setSendbackDraft((previous) =>
                    previous == null
                      ? previous
                      : {
                          ...previous,
                          messageText: nextText
                        }
                  );
                }}
              />
              {overlayNotice?.kind === "error" && (
                <p className="sendback-inline-error" data-testid="sendback-inline-error">
                  {overlayNotice.code ? `[${overlayNotice.code}] ` : ""}
                  {overlayNotice.message}
                </p>
              )}
              <div className="sendback-buttons">
                <button
                  type="button"
                  data-testid="sendback-confirm-btn"
                  onClick={() => {
                    void submitSendback();
                  }}
                  disabled={sendbackSubmitting || !sendbackDraft.messageText.trim()}
                >
                  {sendbackSubmitting ? "Sending..." : "Confirm send to IM"}
                </button>
                {overlayNotice?.kind === "error" && (
                  <button
                    type="button"
                    className="secondary-btn"
                    data-testid="sendback-retry-btn"
                    onClick={() => {
                      void submitSendback();
                    }}
                    disabled={sendbackSubmitting}
                  >
                    Retry send
                  </button>
                )}
                <button
                  type="button"
                  className="secondary-btn"
                  onClick={() => {
                    setSendbackDraft(null);
                    setOverlayNotice(null);
                  }}
                  disabled={sendbackSubmitting}
                >
                  Cancel
                </button>
              </div>
            </section>
          )}

          <footer className="overlay-input">
            <input
              data-testid="overlay-input"
              value={overlayInput}
              onChange={(event) => setOverlayInput(event.target.value)}
              placeholder="Continue conversation..."
            />
            <button
              type="button"
              data-testid="overlay-send-btn"
              onClick={() => {
                void handleOverlaySend();
              }}
            >
              Send
            </button>
          </footer>
        </section>
      )}
    </div>
  );
}

function isTerminal(status: CardStatus): boolean {
  return status === "completed" || status === "failed";
}

function resolveCardStatus(items: HistoryItem[]): CardStatus {
  if (items.length === 0) {
    return "running";
  }
  const latest = items[items.length - 1];
  if (latest.turn_status === "completed") {
    return "completed";
  }
  if (latest.turn_status === "error") {
    return "failed";
  }
  return "running";
}

function resolveSummary(items: HistoryItem[], status: CardStatus): string {
  if (items.length === 0) {
    return status === "waiting" ? "Submitting request..." : "OpenCode is calling skill...";
  }
  const latest = items[items.length - 1];
  const snapshot = latest.snapshot?.trim();
  if (!snapshot) {
    return status === "completed" ? "Completed." : "OpenCode is calling skill...";
  }
  return snapshot;
}

function buildSessionId(): string {
  return `session-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function buildItemKey(item: HistoryItem): string {
  return `${item.turn_id}-${item.seq}`;
}

