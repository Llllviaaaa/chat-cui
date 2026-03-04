package com.chatcui.skill.service;

import com.chatcui.skill.api.dto.DemoTurnAcceptedResponse;
import com.chatcui.skill.api.dto.SkillTurnEventRequest;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SkillDemoTurnService {

    private final TurnPersistenceService turnPersistenceService;
    private final ConcurrentMap<String, AtomicLong> sessionSequence = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Clock clock;

    public SkillDemoTurnService(TurnPersistenceService turnPersistenceService) {
        this(turnPersistenceService, Clock.systemUTC());
    }

    SkillDemoTurnService(TurnPersistenceService turnPersistenceService, Clock clock) {
        this.turnPersistenceService = turnPersistenceService;
        this.clock = clock;
    }

    public DemoTurnAcceptedResponse acceptTurn(String tenantId, String clientId, String sessionId, String prompt) {
        requireValue(tenantId, "tenant_id");
        requireValue(clientId, "client_id");
        requireValue(sessionId, "session_id");
        requireValue(prompt, "prompt");

        String turnId = "turn-" + UUID.randomUUID();
        AtomicLong sequence = sessionSequence.computeIfAbsent(sessionKey(tenantId, clientId, sessionId), key -> new AtomicLong(0L));

        long deltaSeq = sequence.incrementAndGet();
        long finalSeq = sequence.incrementAndGet();
        long completedSeq = sequence.incrementAndGet();
        String baseTrace = "trace-" + sessionId + "-" + deltaSeq;

        turnPersistenceService.persist(new SkillTurnEventRequest(
                tenantId,
                clientId,
                sessionId,
                turnId,
                deltaSeq,
                baseTrace + "-delta",
                SkillTurnEventRequest.Actor.ASSISTANT,
                SkillTurnEventRequest.EventType.DELTA,
                "Thinking about: " + prompt
        ));

        scheduler.schedule(() -> turnPersistenceService.persist(new SkillTurnEventRequest(
                tenantId,
                clientId,
                sessionId,
                turnId,
                finalSeq,
                baseTrace + "-final",
                SkillTurnEventRequest.Actor.ASSISTANT,
                SkillTurnEventRequest.EventType.FINAL,
                "Draft answer: " + prompt
        )), 120, TimeUnit.MILLISECONDS);

        scheduler.schedule(() -> turnPersistenceService.persist(new SkillTurnEventRequest(
                tenantId,
                clientId,
                sessionId,
                turnId,
                completedSeq,
                baseTrace + "-completed",
                SkillTurnEventRequest.Actor.ASSISTANT,
                SkillTurnEventRequest.EventType.COMPLETED,
                "Final answer: " + prompt
        )), 240, TimeUnit.MILLISECONDS);

        return new DemoTurnAcceptedResponse(
                sessionId,
                turnId,
                "accepted",
                Instant.now(clock).toString()
        );
    }

    private void requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private String sessionKey(String tenantId, String clientId, String sessionId) {
        return tenantId + "|" + clientId + "|" + sessionId;
    }

    @PreDestroy
    void shutdownScheduler() {
        scheduler.shutdownNow();
    }
}
