package com.chatcui.skill.api;

import com.chatcui.skill.api.dto.SendbackRequest;
import com.chatcui.skill.api.dto.SendbackResponse;
import com.chatcui.skill.service.SendbackService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SendbackController {

    private final SendbackService sendbackService;

    public SendbackController(SendbackService sendbackService) {
        this.sendbackService = sendbackService;
    }

    @PostMapping("/sessions/{session_id}/sendback")
    public SendbackResponse sendback(
            @PathVariable("session_id") String sessionId,
            @RequestBody SendbackRequest request
    ) {
        return sendbackService.send(new SendbackService.SendCommand(
                request.tenantId(),
                request.clientId(),
                sessionId,
                request.turnId(),
                request.traceId(),
                request.conversationId(),
                request.selectedText(),
                request.messageText()
        ));
    }
}

