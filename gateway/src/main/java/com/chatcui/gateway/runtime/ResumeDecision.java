package com.chatcui.gateway.runtime;

import java.util.Map;

public record ResumeDecision(
        Outcome outcome,
        boolean allowForward,
        String reasonCode,
        String nextAction,
        Map<String, Object> diagnostics) {

    public enum Outcome {
        CONTINUE,
        DROP_DUPLICATE,
        COMPENSATE_GAP,
        TERMINAL_FAILURE
    }

    public ResumeDecision {
        diagnostics = Map.copyOf(diagnostics == null ? Map.of() : diagnostics);
    }

    public static ResumeDecision continueWith(ResumeAnchor anchor) {
        return new ResumeDecision(
                Outcome.CONTINUE,
                true,
                "RESUME_CONTINUE",
                "continue",
                Map.of(
                        "session_id", anchor.sessionId(),
                        "turn_id", anchor.turnId(),
                        "seq", anchor.seq()));
    }

    public static ResumeDecision dropDuplicate(ResumeAnchor previousAnchor, long incomingSeq) {
        return new ResumeDecision(
                Outcome.DROP_DUPLICATE,
                false,
                "SEQ_DUPLICATE_DROPPED",
                "ignore_duplicate",
                Map.of(
                        "session_id", previousAnchor.sessionId(),
                        "turn_id", previousAnchor.turnId(),
                        "previous_seq", previousAnchor.seq(),
                        "incoming_seq", incomingSeq));
    }

    public static ResumeDecision compensateGap(ResumeAnchor previousAnchor, long expectedSeq, long incomingSeq) {
        return new ResumeDecision(
                Outcome.COMPENSATE_GAP,
                false,
                "SEQ_GAP_COMPENSATION_REQUIRED",
                "compensate_and_resume",
                Map.of(
                        "session_id", previousAnchor.sessionId(),
                        "turn_id", previousAnchor.turnId(),
                        "expected_seq", expectedSeq,
                        "incoming_seq", incomingSeq,
                        "last_seq", previousAnchor.seq()));
    }

    public static ResumeDecision terminalOwnerConflict(String sessionId, String activeOwner, String incomingOwner) {
        return new ResumeDecision(
                Outcome.TERMINAL_FAILURE,
                false,
                "RESUME_OWNER_CONFLICT",
                "restart_session",
                Map.of(
                        "session_id", sessionId,
                        "active_owner", activeOwner,
                        "incoming_owner", incomingOwner));
    }

    public static ResumeDecision terminalInvalidAnchor(String sessionId, String turnId, long seq) {
        return new ResumeDecision(
                Outcome.TERMINAL_FAILURE,
                false,
                "RESUME_ANCHOR_INVALID",
                "restart_session",
                Map.of(
                        "session_id", sessionId,
                        "turn_id", turnId,
                        "incoming_seq", seq));
    }
}
