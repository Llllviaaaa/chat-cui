package com.chatcui.skill.api.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTurnEventDtoContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializationKeepsRequiredSnakeCaseFields() throws Exception {
        SkillTurnEventRequest request = new SkillTurnEventRequest(
                "tenant-a",
                "client-a",
                "session-a",
                "turn-a",
                1L,
                "trace-a",
                SkillTurnEventRequest.Actor.USER,
                SkillTurnEventRequest.EventType.DELTA,
                "payload"
        );

        Map<String, Object> payload = objectMapper.readValue(
                objectMapper.writeValueAsBytes(request),
                new TypeReference<>() {
                }
        );

        assertTrue(payload.containsKey("tenant_id"));
        assertTrue(payload.containsKey("client_id"));
        assertTrue(payload.containsKey("session_id"));
        assertTrue(payload.containsKey("turn_id"));
        assertTrue(payload.containsKey("seq"));
        assertTrue(payload.containsKey("trace_id"));
        assertTrue(payload.containsKey("actor"));
        assertTrue(payload.containsKey("event_type"));
    }

    @Test
    void historyItemIncludesTurnAndDeliveryStatusFields() throws Exception {
        SessionHistoryResponse.HistoryItem item = new SessionHistoryResponse.HistoryItem(
                "turn-a",
                1L,
                "assistant",
                "hello",
                "completed",
                "delivered",
                "2026-03-04T00:00:00Z"
        );
        SessionHistoryResponse response = new SessionHistoryResponse(
                "session-a",
                List.of(item)
        );

        Map<String, Object> payload = objectMapper.readValue(
                objectMapper.writeValueAsBytes(response),
                new TypeReference<>() {
                }
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        Map<String, Object> first = items.get(0);

        assertTrue(first.containsKey("turn_status"));
        assertTrue(first.containsKey("delivery_status"));
    }

    @Test
    void enumsAcceptBaselineValues() {
        assertEquals(SkillTurnEventRequest.Actor.USER, SkillTurnEventRequest.Actor.valueOf("USER"));
        assertEquals(SkillTurnEventRequest.Actor.ASSISTANT, SkillTurnEventRequest.Actor.valueOf("ASSISTANT"));
        assertEquals(SkillTurnEventRequest.Actor.SYSTEM, SkillTurnEventRequest.Actor.valueOf("SYSTEM"));
        assertEquals(SkillTurnEventRequest.Actor.PLUGIN, SkillTurnEventRequest.Actor.valueOf("PLUGIN"));

        assertEquals(SkillTurnEventRequest.EventType.DELTA, SkillTurnEventRequest.EventType.valueOf("DELTA"));
        assertEquals(SkillTurnEventRequest.EventType.FINAL, SkillTurnEventRequest.EventType.valueOf("FINAL"));
        assertEquals(SkillTurnEventRequest.EventType.COMPLETED, SkillTurnEventRequest.EventType.valueOf("COMPLETED"));
        assertEquals(SkillTurnEventRequest.EventType.ERROR, SkillTurnEventRequest.EventType.valueOf("ERROR"));
    }
}
