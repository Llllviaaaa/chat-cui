package com.chatcui.gateway.runtime;

import com.chatcui.gateway.routing.RouteOwnershipRecord;
import com.chatcui.gateway.routing.RouteOwnershipStore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ResumeCoordinator {
    private static final String DEFAULT_OWNER = "gateway-owner";

    private final Map<String, ResumeAnchor> anchorsBySession = new ConcurrentHashMap<>();
    private final Map<String, String> ownersBySession = new ConcurrentHashMap<>();
    private final RouteOwnershipStore routeOwnershipStore;
    private final String localGatewayOwnerId;

    public ResumeCoordinator() {
        this(null, null);
    }

    public ResumeCoordinator(RouteOwnershipStore routeOwnershipStore, String localGatewayOwnerId) {
        this.routeOwnershipStore = routeOwnershipStore;
        this.localGatewayOwnerId = normalize(localGatewayOwnerId);
    }

    public synchronized ResumeDecision evaluate(String sessionId, String turnId, long seq, String reconnectOwner) {
        return evaluate(null, sessionId, turnId, seq, reconnectOwner);
    }

    public synchronized ResumeDecision evaluate(
            String tenantId,
            String sessionId,
            String turnId,
            long seq,
            String reconnectOwner) {
        String normalizedSessionId = normalize(sessionId);
        String normalizedTurnId = normalize(turnId);
        if (normalizedSessionId == null || normalizedTurnId == null || seq < 0) {
            return ResumeDecision.terminalInvalidAnchor(normalizedSessionId, normalizedTurnId, seq);
        }

        String normalizedOwner = normalize(reconnectOwner);
        if (normalizedOwner == null) {
            normalizedOwner = localGatewayOwnerId == null ? DEFAULT_OWNER : localGatewayOwnerId;
        }

        Optional<RouteOwnershipRecord> routeRecord = loadRouteRecord(tenantId, normalizedSessionId);
        if (routeRecord.isPresent()) {
            RouteOwnershipRecord route = routeRecord.get();
            ownersBySession.put(normalizedSessionId, route.gatewayOwner());
            if (isFenced(normalizedOwner, route) || !route.gatewayOwner().equals(normalizedOwner)) {
                return ResumeDecision.terminalOwnerFenced(
                        normalizedSessionId,
                        route.gatewayOwner(),
                        normalizedOwner,
                        route.fencedOwner(),
                        route.routeVersion());
            }
        } else {
            String activeOwner = ownersBySession.putIfAbsent(normalizedSessionId, normalizedOwner);
            if (activeOwner != null && !activeOwner.equals(normalizedOwner)) {
                return ResumeDecision.terminalOwnerConflict(normalizedSessionId, activeOwner, normalizedOwner);
            }
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

    private Optional<RouteOwnershipRecord> loadRouteRecord(String tenantId, String sessionId) {
        String normalizedTenantId = normalize(tenantId);
        if (routeOwnershipStore == null || normalizedTenantId == null) {
            return Optional.empty();
        }
        return routeOwnershipStore.load(normalizedTenantId, sessionId);
    }

    private boolean isFenced(String owner, RouteOwnershipRecord routeRecord) {
        String fencedOwner = normalize(routeRecord.fencedOwner());
        return fencedOwner != null && fencedOwner.equals(owner);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
