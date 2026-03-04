package com.chatcui.skill.integration;

import com.chatcui.skill.api.dto.SessionHistoryResponse;
import com.chatcui.skill.api.dto.SkillTurnEventRequest;
import com.chatcui.skill.persistence.mapper.TurnRecordMapper;
import com.chatcui.skill.persistence.model.TurnRecord;
import com.chatcui.skill.service.SessionHistoryQueryService;
import com.chatcui.skill.service.TurnPersistenceService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionHistoryReplayIntegrationTest {

    @Test
    void historyReplayReturnsAscendingTurnProgressionForForwardedSessionFixture() {
        InMemoryTurnRecordMapper mapper = new InMemoryTurnRecordMapper();
        TurnPersistenceService persistenceService = new TurnPersistenceService(mapper);
        SessionHistoryQueryService queryService = new SessionHistoryQueryService(mapper);

        persistenceService.persist(event("turn-shared-001", 1L, SkillTurnEventRequest.EventType.DELTA, "draft-1"));
        persistenceService.persist(event("turn-shared-001", 2L, SkillTurnEventRequest.EventType.FINAL, "answer-1"));
        persistenceService.persist(event("turn-shared-001", 3L, SkillTurnEventRequest.EventType.COMPLETED, "answer-1"));
        persistenceService.persist(event("turn-shared-002", 4L, SkillTurnEventRequest.EventType.DELTA, "draft-2"));
        persistenceService.persist(event("turn-shared-002", 5L, SkillTurnEventRequest.EventType.COMPLETED, "answer-2"));

        SessionHistoryResponse page = queryService.query(new SessionHistoryQueryService.QueryCommand(
                "tenant-shared",
                "client-shared",
                "session-shared-001",
                null,
                10));

        assertEquals(List.of("turn-shared-001", "turn-shared-001", "turn-shared-001", "turn-shared-002", "turn-shared-002"),
                page.items().stream().map(SessionHistoryResponse.HistoryItem::turnId).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L),
                page.items().stream().map(SessionHistoryResponse.HistoryItem::seq).toList());
        assertEquals("delivered", page.items().get(2).deliveryStatus());
        assertEquals("delivered", page.items().get(4).deliveryStatus());
    }

    @Test
    void cursorPaginationIsDeterministicAcrossSharedSessionFixture() {
        InMemoryTurnRecordMapper mapper = new InMemoryTurnRecordMapper();
        TurnPersistenceService persistenceService = new TurnPersistenceService(mapper);
        SessionHistoryQueryService queryService = new SessionHistoryQueryService(mapper);

        persistenceService.persist(event("turn-shared-001", 1L, SkillTurnEventRequest.EventType.DELTA, "draft-1"));
        persistenceService.persist(event("turn-shared-001", 2L, SkillTurnEventRequest.EventType.FINAL, "answer-1"));
        persistenceService.persist(event("turn-shared-001", 3L, SkillTurnEventRequest.EventType.COMPLETED, "answer-1"));
        persistenceService.persist(event("turn-shared-002", 4L, SkillTurnEventRequest.EventType.DELTA, "draft-2"));
        persistenceService.persist(event("turn-shared-002", 5L, SkillTurnEventRequest.EventType.COMPLETED, "answer-2"));

        SessionHistoryResponse first = queryService.query(new SessionHistoryQueryService.QueryCommand(
                "tenant-shared",
                "client-shared",
                "session-shared-001",
                null,
                3));
        assertTrue(first.hasMore());
        assertEquals("turn-shared-001", first.nextCursor());
        assertEquals(List.of(1L, 2L, 3L), first.items().stream().map(SessionHistoryResponse.HistoryItem::seq).toList());

        SessionHistoryResponse second = queryService.query(new SessionHistoryQueryService.QueryCommand(
                "tenant-shared",
                "client-shared",
                "session-shared-001",
                first.nextCursor(),
                3));
        assertFalse(second.hasMore());
        assertEquals(List.of(4L, 5L), second.items().stream().map(SessionHistoryResponse.HistoryItem::seq).toList());
    }

    private SkillTurnEventRequest event(String turnId, long seq, SkillTurnEventRequest.EventType type, String payload) {
        return new SkillTurnEventRequest(
                "tenant-shared",
                "client-shared",
                "session-shared-001",
                turnId,
                seq,
                "trace-" + seq,
                SkillTurnEventRequest.Actor.ASSISTANT,
                type,
                payload
        );
    }

    private static final class InMemoryTurnRecordMapper implements TurnRecordMapper {
        private final List<TurnRecord> records = new ArrayList<>();

        @Override
        public boolean existsBySessionTurnSeq(String sessionId, String turnId, Long seq) {
            return records.stream()
                    .anyMatch(record -> record.sessionId().equals(sessionId)
                            && record.turnId().equals(turnId)
                            && record.seq().equals(seq));
        }

        @Override
        public Optional<TurnRecord> findLatestBySessionTurn(String sessionId, String turnId) {
            return records.stream()
                    .filter(record -> record.sessionId().equals(sessionId) && record.turnId().equals(turnId))
                    .max(Comparator.comparing(TurnRecord::seq));
        }

        @Override
        public int insert(TurnRecord record) {
            records.add(new TurnRecord(
                    record.tenantId(),
                    record.clientId(),
                    record.sessionId(),
                    record.turnId(),
                    record.seq(),
                    record.traceId(),
                    record.actor(),
                    record.eventType(),
                    record.payload(),
                    record.turnStatus(),
                    record.deliveryStatus(),
                    LocalDateTime.of(2026, 3, 4, 0, 0).plusSeconds(record.seq())
            ));
            return 1;
        }

        @Override
        public List<TurnRecord> listHistoryBySession(String tenantId, String clientId, String sessionId, String cursorTurnId, int limit) {
            return records.stream()
                    .filter(record -> record.tenantId().equals(tenantId)
                            && record.clientId().equals(clientId)
                            && record.sessionId().equals(sessionId))
                    .sorted(Comparator
                            .comparing(TurnRecord::createdAt, Comparator.nullsLast(LocalDateTime::compareTo))
                            .thenComparing(TurnRecord::turnId))
                    .filter(record -> cursorTurnId == null || cursorTurnId.isBlank() || record.turnId().compareTo(cursorTurnId) > 0)
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean existsSession(String tenantId, String clientId, String sessionId) {
            return records.stream()
                    .anyMatch(record -> record.tenantId().equals(tenantId)
                            && record.clientId().equals(clientId)
                            && record.sessionId().equals(sessionId));
        }

        @Override
        public boolean existsTurnInSession(String tenantId, String clientId, String sessionId, String turnId) {
            return records.stream()
                    .anyMatch(record -> record.tenantId().equals(tenantId)
                            && record.clientId().equals(clientId)
                            && record.sessionId().equals(sessionId)
                            && record.turnId().equals(turnId));
        }
    }
}
