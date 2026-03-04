export type CardStatus = "waiting" | "running" | "completed" | "failed";

export interface HistoryItem {
  turn_id: string;
  seq: number;
  trace_id: string;
  actor: string;
  snapshot: string;
  turn_status: string;
  delivery_status: string;
  created_at: string | null;
}

export interface SessionHistoryResponse {
  session_id: string;
  next_cursor: string | null;
  has_more: boolean;
  items: HistoryItem[];
}

export interface TurnAcceptedResponse {
  session_id: string;
  turn_id: string;
  receive_state: string;
  accepted_at: string;
}

export interface SkillSessionViewModel {
  sessionId: string;
  status: CardStatus;
  summary: string;
  history: HistoryItem[];
}
