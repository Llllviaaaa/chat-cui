package com.chatcui.skill.api;

import com.chatcui.skill.api.dto.SessionHistoryResponse;
import com.chatcui.skill.service.SessionHistoryQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionHistoryController {

    private final SessionHistoryQueryService queryService;

    public SessionHistoryController(SessionHistoryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/sessions/{session_id}/history")
    public SessionHistoryResponse getHistory(
            @PathVariable("session_id") String sessionId,
            @RequestParam("tenant_id") String tenantId,
            @RequestParam("client_id") String clientId,
            @RequestParam(value = "cursor_turn_id", required = false) String cursorTurnId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return queryService.query(new SessionHistoryQueryService.QueryCommand(
                tenantId,
                clientId,
                sessionId,
                cursorTurnId,
                limit
        ));
    }
}
