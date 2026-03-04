package com.chatcui.skill.api;

import com.chatcui.skill.api.dto.SendbackResponse;
import com.chatcui.skill.service.SendbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SendbackControllerIntegrationTest {

    private SendbackService sendbackService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sendbackService = mock(SendbackService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SendbackController(sendbackService))
                .setControllerAdvice(new SendbackExceptionHandler())
                .build();
    }

    @Test
    void returnsSentResponseWhenSendbackSucceeds() throws Exception {
        when(sendbackService.send(any())).thenReturn(new SendbackResponse(
                "sendback-1",
                "session-1",
                "turn-1",
                "trace-1",
                "sent",
                "im-msg-1",
                "2026-03-04T00:00:00Z"
        ));

        mockMvc.perform(post("/sessions/session-1/sendback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenant_id":"tenant-1",
                                  "client_id":"client-1",
                                  "turn_id":"turn-1",
                                  "trace_id":"trace-1",
                                  "conversation_id":"conversation-1",
                                  "selected_text":"answer",
                                  "message_text":"answer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_id").value("sendback-1"))
                .andExpect(jsonPath("$.send_status").value("sent"))
                .andExpect(jsonPath("$.im_message_id").value("im-msg-1"));
    }

    @Test
    void validationFailureReturnsDeterministicInvalidRequestError() throws Exception {
        doThrow(new IllegalArgumentException("selected_text is required"))
                .when(sendbackService)
                .send(any());

        mockMvc.perform(post("/sessions/session-1/sendback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenant_id":"tenant-1",
                                  "client_id":"client-1",
                                  "turn_id":"turn-1",
                                  "trace_id":"trace-1",
                                  "conversation_id":"conversation-1",
                                  "selected_text":"",
                                  "message_text":""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidSelectionReturnsDeterministicErrorContract() throws Exception {
        doThrow(new SendbackService.SelectionMismatchException("selected_text is not part of assistant output"))
                .when(sendbackService)
                .send(any());

        mockMvc.perform(post("/sessions/session-1/sendback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenant_id":"tenant-1",
                                  "client_id":"client-1",
                                  "turn_id":"turn-1",
                                  "trace_id":"trace-1",
                                  "conversation_id":"conversation-1",
                                  "selected_text":"not-found",
                                  "message_text":"not-found"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SELECTION"));
    }

    @Test
    void imSendFailureReturnsActionableErrorContract() throws Exception {
        doThrow(new SendbackService.SendbackFailedException("IM_CHANNEL_UNAVAILABLE", "IM channel is unavailable. Please retry."))
                .when(sendbackService)
                .send(any());

        mockMvc.perform(post("/sessions/session-1/sendback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenant_id":"tenant-1",
                                  "client_id":"client-1",
                                  "turn_id":"turn-1",
                                  "trace_id":"trace-1",
                                  "conversation_id":"conversation-1",
                                  "selected_text":"answer",
                                  "message_text":"answer"
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("IM_CHANNEL_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value("IM channel is unavailable. Please retry."));
    }

    @Test
    void duplicateRetryReturnsDeterministicPriorResponseContract() throws Exception {
        SendbackResponse replayed = new SendbackResponse(
                "sendback-existing",
                "session-1",
                "turn-1",
                "trace-1",
                "sent",
                "im-msg-1",
                "2026-03-04T00:00:00Z"
        );
        when(sendbackService.send(any())).thenReturn(replayed, replayed);

        mockMvc.perform(post("/sessions/session-1/sendback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenant_id":"tenant-1",
                                  "client_id":"client-1",
                                  "turn_id":"turn-1",
                                  "trace_id":"trace-1",
                                  "conversation_id":"conversation-1",
                                  "selected_text":"answer",
                                  "message_text":"answer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_id").value("sendback-existing"))
                .andExpect(jsonPath("$.trace_id").value("trace-1"));

        mockMvc.perform(post("/sessions/session-1/sendback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenant_id":"tenant-1",
                                  "client_id":"client-1",
                                  "turn_id":"turn-1",
                                  "trace_id":"trace-retry",
                                  "conversation_id":"conversation-1",
                                  "selected_text":"answer",
                                  "message_text":"answer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_id").value("sendback-existing"))
                .andExpect(jsonPath("$.trace_id").value("trace-1"));
    }
}
