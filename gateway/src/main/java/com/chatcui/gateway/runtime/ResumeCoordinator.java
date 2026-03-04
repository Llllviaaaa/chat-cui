package com.chatcui.gateway.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ResumeCoordinator {
    private static final String DEFAULT_OWNER = "gateway-owner";

    private final Map<String, ResumeAnchor> anchorsBySession = new ConcurrentHashMap<>();
    private final Map<String, String> ownersBySession = new ConcurrentHashMap<>();

    public synchronized ResumeDecision evaluate(String sessionId, String turnId, long seq, String reconnectOwner) {
        String normalizedSessionId = normalize(sessionId);
        String normalizedTurnId = normalize(turnId);
        if (normalizedSessionId == null || normalizedTurnId == null || seq < 0) {
            return ResumeDecision.terminalInvalidAnchor(normalizedSessionId, normalizedTurnId, seq);
        }

        String normalizedOwner = normalize(reconnectOwner);
        if (normalizedOwner == null) {
            normalizedOwner = DEFAULT_OWNER;
        }

        String activeOwner = ownersBySession.putIfAbsent(normalizedSessionId, normalizedOwner);
        if (activeOwner != null && !activeOwner.equals(normalizedOwner)) {
            return ResumeDecision.terminalOwnerConflict(normalizedSessionId, activeOwner, normalizedOwner);
        }

        ResumeAnchor previous = anchorsBySession.get(normalizedSessionId);
        ResumeAnchor incoming = new ResumeAnchor(normalizedSessionId, normalizedTurnId, seq);
        if (previous == null || !previous.turnId().equals(normalizedTurnId)) {
            anchorsBySession.put(normalizedSessionId, incoming);
            return ResumeDecision.continueWith(incoming);
        }

        if (seq <= previous.seq()) {
            return ResumeDecision.dropDuplicate(previous, seq);
        }

        long expectedSeq = previous.seq() + 1;
        if (seq > expectedSeq) {
            return ResumeDecision.compensateGap(previous, expectedSeq, seq);
        }

        anchorsBySession.put(normalizedSessionId, incoming);
        return ResumeDecision.continueWith(incoming);
    }

    public Optional<ResumeAnchor> lastAnchor(String sessionId) {
        String normalizedSessionId = normalize(sessionId);
        if (normalizedSessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(anchorsBySession.get(normalizedSessionId));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
