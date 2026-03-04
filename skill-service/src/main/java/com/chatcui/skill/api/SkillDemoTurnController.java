package com.chatcui.skill.api;

import com.chatcui.skill.api.dto.DemoTurnAcceptedResponse;
import com.chatcui.skill.api.dto.DemoTurnRequest;
import com.chatcui.skill.service.SkillDemoTurnService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SkillDemoTurnController {

    private final SkillDemoTurnService demoTurnService;

    public SkillDemoTurnController(SkillDemoTurnService demoTurnService) {
        this.demoTurnService = demoTurnService;
    }

    @PostMapping("/demo/skill/sessions/{session_id}/turns")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DemoTurnAcceptedResponse createTurn(
            @PathVariable("session_id") String sessionId,
            @RequestBody DemoTurnRequest request
    ) {
        return demoTurnService.acceptTurn(
                request.tenantId(),
                request.clientId(),
                sessionId,
                request.prompt()
        );
    }
}
