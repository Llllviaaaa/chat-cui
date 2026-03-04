import { useEffect, useMemo, useRef, useState } from "react";
import { HttpSkillDemoApi, type SkillDemoApi } from "./lib/api";
import type { CardStatus, HistoryItem, SkillSessionViewModel } from "./lib/types";

type AppProps = {
  api?: SkillDemoApi;
  tenantId?: string;
  clientId?: string;
  pollIntervalMs?: number;
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

  return (
    <div className="app-shell">
      <header className="top-bar">
        <h1>Chat CUI Web Demo</h1>
        <p>Phase 4: slash trigger, status card, expanded skill session</p>
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
            </div>
            <button type="button" onClick={handleOverlayClose}>
              Back to chat
            </button>
          </header>

          <div className="overlay-history" ref={overlayHistoryRef}>
            {activeSession.history.length === 0 && (
              <div className="empty-state">Waiting for session history...</div>
            )}
            {activeSession.history.map((item) => (
              <div key={`${item.turn_id}-${item.seq}`} className="history-item">
                <div className="meta">
                  <span>{item.turn_id}</span>
                  <span>{item.seq}</span>
                  <span>{item.turn_status}</span>
                  <span>{item.delivery_status}</span>
                </div>
                <p>{item.snapshot}</p>
              </div>
            ))}
          </div>

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
