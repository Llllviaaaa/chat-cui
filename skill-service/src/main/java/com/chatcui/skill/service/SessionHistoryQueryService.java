package com.chatcui.skill.service;

import com.chatcui.skill.api.dto.SessionHistoryResponse;
import com.chatcui.skill.persistence.mapper.TurnRecordMapper;
import com.chatcui.skill.persistence.model.TurnRecord;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class SessionHistoryQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final Pattern TURN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final TurnRecordMapper turnRecordMapper;

    public SessionHistoryQueryService(TurnRecordMapper turnRecordMapper) {
        this.turnRecordMapper = turnRecordMapper;
    }

    public SessionHistoryResponse query(QueryCommand command) {
        validate(command);
        if (!turnRecordMapper.existsSession(command.tenantId(), command.clientId(), command.sessionId())) {
            throw new SessionNotFoundException(command.sessionId());
        }
        if (command.cursorTurnId() != null && !command.cursorTurnId().isBlank()
                && !turnRecordMapper.existsTurnInSession(command.tenantId(), command.clientId(), command.sessionId(), command.cursorTurnId())) {
            throw new IllegalArgumentException("invalid cursor_turn_id");
        }

        int pageLimit = normalizeLimit(command.limit());
        List<TurnRecord> rows = turnRecordMapper.listHistoryBySession(
                command.tenantId(),
                command.clientId(),
                command.sessionId(),
                command.cursorTurnId(),
                pageLimit + 1
        );

        boolean hasMore = rows.size() > pageLimit;
        List<TurnRecord> page = hasMore ? rows.subList(0, pageLimit) : rows;
        String nextCursor = hasMore && !page.isEmpty() ? page.get(page.size() - 1).turnId() : null;

        List<SessionHistoryResponse.HistoryItem> items = page.stream()
                .map(this::toHistoryItem)
                .toList();

        return new SessionHistoryResponse(command.sessionId(), nextCursor, hasMore, items);
    }

    private SessionHistoryResponse.HistoryItem toHistoryItem(TurnRecord row) {
        String createdAt = row.createdAt() == null ? null : row.createdAt().toString();
        return new SessionHistoryResponse.HistoryItem(
                row.turnId(),
                row.seq(),
                row.traceId(),
                row.actor(),
                row.payload(),
                row.turnStatus(),
                row.deliveryStatus(),
                createdAt
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private void validate(QueryCommand command) {
        Objects.requireNonNull(command, "query command is required");
        requireValue(command.tenantId(), "tenant_id");
        requireValue(command.clientId(), "client_id");
        requireValue(command.sessionId(), "session_id");
        if (command.cursorTurnId() != null
                && !command.cursorTurnId().isBlank()
                && !TURN_ID_PATTERN.matcher(command.cursorTurnId()).matches()) {
            throw new IllegalArgumentException("invalid cursor_turn_id");
        }
    }

    private void requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    public record QueryCommand(
            String tenantId,
            String clientId,
            String sessionId,
            String cursorTurnId,
            Integer limit
    ) {
    }

    public static class SessionNotFoundException extends RuntimeException {

        public SessionNotFoundException(String sessionId) {
            super("session not found: " + sessionId);
        }
    }
}
