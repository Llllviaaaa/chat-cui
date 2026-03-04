package com.chatcui.skill.service;

import com.chatcui.skill.api.dto.DemoTurnAcceptedResponse;
import com.chatcui.skill.api.dto.SessionHistoryResponse;
import com.chatcui.skill.persistence.mapper.TurnRecordMapper;
import com.chatcui.skill.persistence.model.TurnRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillDemoTurnServiceTest {

    private InMemoryTurnRecordMapper mapper;
    private SkillDemoTurnService demoTurnService;
    private SessionHistoryQueryService queryService;

    @BeforeEach
    void setUp() {
        mapper = new InMemoryTurnRecordMapper();
        TurnPersistenceService persistenceService = new TurnPersistenceService(mapper);
        demoTurnService = new SkillDemoTurnService(persistenceService);
        queryService = new SessionHistoryQueryService(mapper);
    }

    @Test
    void acceptsTurnImmediatelyAndEventuallyCompletesPersistedStream() throws Exception {
        DemoTurnAcceptedResponse response = demoTurnService.acceptTurn(
                "tenant-demo",
                "client-demo",
                "session-demo",
                "How do I build this feature?"
        );

        assertEquals("accepted", response.receiveState());
        assertEquals("session-demo", response.sessionId());
        assertNotNull(response.turnId());
        assertNotNull(response.acceptedAt());

        assertTrue(waitUntil(() -> {
            SessionHistoryResponse history = queryService.query(new SessionHistoryQueryService.QueryCommand(
                    "tenant-demo",
                    "client-demo",
                    "session-demo",
                    null,
                    20
            ));
            return history.items().stream().anyMatch(item ->
                    item.turnId().equals(response.turnId()) && "completed".equals(item.turnStatus()));
        }, 1500));

        SessionHistoryResponse history = queryService.query(new SessionHistoryQueryService.QueryCommand(
                "tenant-demo",
                "client-demo",
                "session-demo",
                null,
                20
        ));

        List<SessionHistoryResponse.HistoryItem> turnItems = history.items().stream()
                .filter(item -> item.turnId().equals(response.turnId()))
                .toList();
        assertEquals(List.of(1L, 2L, 3L), turnItems.stream().map(SessionHistoryResponse.HistoryItem::seq).toList());
        assertEquals("completed", turnItems.get(turnItems.size() - 1).turnStatus());
        assertEquals("delivered", turnItems.get(turnItems.size() - 1).deliveryStatus());
    }

    private boolean waitUntil(Check check, long timeoutMs) throws InterruptedException {
        long started = System.currentTimeMillis();
        while (System.currentTimeMillis() - started < timeoutMs) {
            if (check.evaluate()) {
                return true;
            }
            Thread.sleep(15);
        }
        return false;
    }

    @FunctionalInterface
    private interface Check {
        boolean evaluate();
    }

    private static final class InMemoryTurnRecordMapper implements TurnRecordMapper {
        private static final LocalDateTime BASE = LocalDateTime.of(2026, 3, 4, 0, 0, 0);
        private final List<TurnRecord> records = new ArrayList<>();
        private final AtomicInteger offset = new AtomicInteger();

        @Override
        public boolean existsBySessionTurnSeq(String sessionId, String turnId, Long seq) {
            return records.stream().anyMatch(record ->
                    record.sessionId().equals(sessionId) && record.turnId().equals(turnId) && record.seq().equals(seq));
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
                    BASE.plusSeconds(offset.getAndIncrement())
            ));
            return 1;
        }

        @Override
        public List<TurnRecord> listHistoryBySession(String tenantId, String clientId, String sessionId, String cursorTurnId, int limit) {
            return records.stream()
                    .filter(record -> record.tenantId().equals(tenantId))
                    .filter(record -> record.clientId().equals(clientId))
                    .filter(record -> record.sessionId().equals(sessionId))
                    .filter(record -> cursorTurnId == null || cursorTurnId.isBlank() || record.turnId().compareTo(cursorTurnId) > 0)
                    .sorted(Comparator.comparing(TurnRecord::createdAt).thenComparing(TurnRecord::turnId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean existsSession(String tenantId, String clientId, String sessionId) {
            return records.stream().anyMatch(record ->
                    record.tenantId().equals(tenantId)
                            && record.clientId().equals(clientId)
                            && record.sessionId().equals(sessionId));
        }

        @Override
        public boolean existsTurnInSession(String tenantId, String clientId, String sessionId, String turnId) {
            return records.stream().anyMatch(record ->
                    record.tenantId().equals(tenantId)
                            && record.clientId().equals(clientId)
                            && record.sessionId().equals(sessionId)
                            && record.turnId().equals(turnId));
        }
    }
}
