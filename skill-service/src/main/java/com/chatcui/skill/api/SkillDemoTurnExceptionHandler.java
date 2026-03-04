package com.chatcui.skill.api;

import com.chatcui.skill.api.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SkillDemoTurnController.class)
public class SkillDemoTurnExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                new ErrorResponse.ErrorBody("INVALID_REQUEST", ex.getMessage())
        ));
    }
}
