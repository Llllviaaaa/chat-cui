package com.chatcui.skill.api;

import com.chatcui.skill.api.dto.ErrorResponse;
import com.chatcui.skill.service.SessionHistoryQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SessionHistoryController.class)
public class SessionHistoryExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(error("INVALID_REQUEST", ex.getParameterName() + " is required"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("cursor_turn_id")) {
            return ResponseEntity.badRequest().body(error("INVALID_CURSOR", "cursor_turn_id is invalid"));
        }
        return ResponseEntity.badRequest().body(error("INVALID_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(SessionHistoryQueryService.SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(SessionHistoryQueryService.SessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("SESSION_NOT_FOUND", ex.getMessage()));
    }

    private ErrorResponse error(String code, String message) {
        return new ErrorResponse(new ErrorResponse.ErrorBody(code, message));
    }
}
