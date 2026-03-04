package com.chatcui.skill.api;

import com.chatcui.skill.api.dto.ErrorResponse;
import com.chatcui.skill.service.SendbackService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SendbackController.class)
public class SendbackExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(error("INVALID_REQUEST", ex.getParameterName() + " is required"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(error("INVALID_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(SendbackService.TurnNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTurnNotFound(SendbackService.TurnNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("TURN_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(SendbackService.SelectionMismatchException.class)
    public ResponseEntity<ErrorResponse> handleSelectionMismatch(SendbackService.SelectionMismatchException ex) {
        return ResponseEntity.badRequest().body(error("INVALID_SELECTION", ex.getMessage()));
    }

    @ExceptionHandler(SendbackService.SendbackFailedException.class)
    public ResponseEntity<ErrorResponse> handleSendbackFailed(SendbackService.SendbackFailedException ex) {
        String code = ex.code() == null || ex.code().isBlank() ? "IM_SEND_FAILED" : ex.code();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(code, ex.getMessage()));
    }

    private ErrorResponse error(String code, String message) {
        return new ErrorResponse(new ErrorResponse.ErrorBody(code, message));
    }
}

