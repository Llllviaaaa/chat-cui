package com.chatcui.skill.api;

import com.chatcui.skill.api.dto.SessionHistoryResponse;
import com.chatcui.skill.service.SessionHistoryQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionHistoryControllerIntegrationTest {

    private SessionHistoryQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(SessionHistoryQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SessionHistoryController(queryService)).build();
    }

    @Test
    void returnsAscendingReplaySafeOrder() throws Exception {
        when(queryService.query(any())).thenReturn(new SessionHistoryResponse(
                "session-1",
                "turn-2",
                false,
                List.of(
                        new SessionHistoryResponse.HistoryItem("turn-1", 1L, "trace-1", "assistant", "hello", "completed", "delivered", "2026-03-04T00:00:00Z"),
                        new SessionHistoryResponse.HistoryItem("turn-2", 2L, "trace-2", "assistant", "world", "completed", "delivered", "2026-03-04T00:01:00Z")
                )
        ));

        mockMvc.perform(get("/sessions/session-1/history")
                        .queryParam("tenant_id", "tenant-1")
                        .queryParam("client_id", "client-1")
                        .queryParam("limit", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].turn_id").value("turn-1"))
                .andExpect(jsonPath("$.items[1].turn_id").value("turn-2"));
    }

    @Test
    void cursorPaginationReturnsNextTurnsWithoutDuplication() throws Exception {
        when(queryService.query(any())).thenReturn(new SessionHistoryResponse(
                "session-1",
                "turn-4",
                false,
                List.of(
                        new SessionHistoryResponse.HistoryItem("turn-3", 3L, "trace-3", "assistant", "next-1", "in_progress", "pending", "2026-03-04T00:02:00Z"),
                        new SessionHistoryResponse.HistoryItem("turn-4", 4L, "trace-4", "assistant", "next-2", "completed", "delivered", "2026-03-04T00:03:00Z")
                )
        ));

        mockMvc.perform(get("/sessions/session-1/history")
                        .queryParam("tenant_id", "tenant-1")
                        .queryParam("client_id", "client-1")
                        .queryParam("cursor_turn_id", "turn-2")
                        .queryParam("limit", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].turn_id").value("turn-3"))
                .andExpect(jsonPath("$.items[1].turn_id").value("turn-4"))
                .andExpect(jsonPath("$.next_cursor").value("turn-4"))
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    void responseItemsIncludeRequiredStatusAndSnapshotFields() throws Exception {
        when(queryService.query(any())).thenReturn(new SessionHistoryResponse(
                "session-1",
                null,
                false,
                List.of(
                        new SessionHistoryResponse.HistoryItem("turn-1", 1L, "trace-1", "assistant", "snapshot text", "completed", "delivered", "2026-03-04T00:00:00Z")
                )
        ));

        mockMvc.perform(get("/sessions/session-1/history")
                        .queryParam("tenant_id", "tenant-1")
                        .queryParam("client_id", "client-1")
                        .queryParam("limit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].seq").value(1))
                .andExpect(jsonPath("$.items[0].trace_id").value("trace-1"))
                .andExpect(jsonPath("$.items[0].snapshot").value("snapshot text"))
                .andExpect(jsonPath("$.items[0].turn_status").value("completed"))
                .andExpect(jsonPath("$.items[0].delivery_status").value("delivered"));
    }
}
