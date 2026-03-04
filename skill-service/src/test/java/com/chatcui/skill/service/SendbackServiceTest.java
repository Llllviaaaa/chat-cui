package com.chatcui.skill.service;

import com.chatcui.skill.api.dto.SendbackResponse;
import com.chatcui.skill.persistence.mapper.SendbackRecordMapper;
import com.chatcui.skill.persistence.mapper.TurnRecordMapper;
import com.chatcui.skill.persistence.model.SendbackRecord;
import com.chatcui.skill.persistence.model.TurnRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendbackServiceTest {

    @Mock
    private TurnRecordMapper turnRecordMapper;

    @Mock
    private SendbackRecordMapper sendbackRecordMapper;

    @Mock
    private ImMessageGateway imMessageGateway;

    private SendbackService service;

    @BeforeEach
    void setUp() {
        service = new SendbackService(turnRecordMapper, sendbackRecordMapper, imMessageGateway);
    }

    @Test
    void successfulSendbackReturnsSentResponseAndPersistsCorrelationRecord() {
        SendbackService.SendCommand command = command("answer");
        when(turnRecordMapper.existsTurnInSession("tenant-1", "client-1", "session-1", "turn-1")).thenReturn(true);
        when(turnRecordMapper.findLatestBySessionTurn("session-1", "turn-1"))
                .thenReturn(Optional.of(turn("Final answer for team", "assistant")));
        when(imMessageGateway.send(any()))
                .thenReturn(new ImMessageGateway.ImSendResult("im-msg-1", Instant.parse("2026-03-04T00:00:00Z")));

        SendbackResponse response = service.send(command);

        assertEquals("session-1", response.sessionId());
        assertEquals("turn-1", response.turnId());
        assertEquals("sent", response.sendStatus());
        assertEquals("im-msg-1", response.imMessageId());
        verify(sendbackRecordMapper).insert(argThatRecord("sent", "im-msg-1", null));
    }

    @Test
    void selectionNotInAssistantPayloadIsRejected() {
        SendbackService.SendCommand command = command("not-present");
        when(turnRecordMapper.existsTurnInSession("tenant-1", "client-1", "session-1", "turn-1")).thenReturn(true);
        when(turnRecordMapper.findLatestBySessionTurn("session-1", "turn-1"))
                .thenReturn(Optional.of(turn("Final answer for team", "assistant")));

        assertThrows(SendbackService.SelectionMismatchException.class, () -> service.send(command));

        verify(imMessageGateway, never()).send(any());
        verify(sendbackRecordMapper, never()).insert(any(SendbackRecord.class));
    }

    @Test
    void imGatewayFailurePersistsFailedRecordAndReturnsActionableError() {
        SendbackService.SendCommand command = command("answer");
        when(turnRecordMapper.existsTurnInSession("tenant-1", "client-1", "session-1", "turn-1")).thenReturn(true);
        when(turnRecordMapper.findLatestBySessionTurn("session-1", "turn-1"))
                .thenReturn(Optional.of(turn("Final answer for team", "assistant")));
        when(imMessageGateway.send(any()))
                .thenThrow(new ImMessageGateway.ImSendException("IM_CHANNEL_UNAVAILABLE", "IM channel is unavailable. Please retry."));

        SendbackService.SendbackFailedException error =
                assertThrows(SendbackService.SendbackFailedException.class, () -> service.send(command));

        assertEquals("IM_CHANNEL_UNAVAILABLE", error.code());
        verify(sendbackRecordMapper).insert(argThatRecord("failed", null, "IM_CHANNEL_UNAVAILABLE"));
    }

    @Test
    void nonAssistantTurnCannotBeUsedForSendback() {
        SendbackService.SendCommand command = command("answer");
        when(turnRecordMapper.existsTurnInSession("tenant-1", "client-1", "session-1", "turn-1")).thenReturn(true);
        when(turnRecordMapper.findLatestBySessionTurn("session-1", "turn-1"))
                .thenReturn(Optional.of(turn("user question", "user")));

        assertThrows(SendbackService.SelectionMismatchException.class, () -> service.send(command));

        verify(imMessageGateway, never()).send(any());
        verify(sendbackRecordMapper, never()).insert(any(SendbackRecord.class));
    }

    private SendbackService.SendCommand command(String selectedText) {
        return new SendbackService.SendCommand(
                "tenant-1",
                "client-1",
                "session-1",
                "turn-1",
                "trace-1",
                "conversation-1",
                selectedText,
                selectedText
        );
    }

    private TurnRecord turn(String payload, String actor) {
        return new TurnRecord(
                "tenant-1",
                "client-1",
                "session-1",
                "turn-1",
                3L,
                "trace-1",
                actor,
                "completed",
                payload,
                "completed",
                "delivered",
                null
        );
    }

    private SendbackRecord argThatRecord(String status, String messageId, String errorCode) {
        return org.mockito.ArgumentMatchers.argThat(record ->
                status.equals(record.sendStatus())
                        && eqNullable(messageId, record.imMessageId())
                        && eqNullable(errorCode, record.errorCode())
                        && "session-1".equals(record.sessionId())
                        && "turn-1".equals(record.turnId())
        );
    }

    private boolean eqNullable(String expected, String actual) {
        if (expected == null) {
            return actual == null;
        }
        return expected.equals(actual);
    }
}

