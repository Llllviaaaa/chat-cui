package com.chatcui.gateway.http;

import com.chatcui.gateway.auth.AuthService;
import com.chatcui.gateway.auth.ErrorResponseFactory;
import com.chatcui.gateway.auth.model.AuthErrorResponse;
import com.chatcui.gateway.auth.model.AuthPrincipal;
import com.chatcui.gateway.auth.model.AuthRequest;
import com.chatcui.gateway.auth.model.AuthResult;

public class AuthEntryInterceptor {
    private final AuthService authService;
    private final ErrorResponseFactory errorResponseFactory;

    public AuthEntryInterceptor(AuthService authService, ErrorResponseFactory errorResponseFactory) {
        this.authService = authService;
        this.errorResponseFactory = errorResponseFactory;
    }

    public EntryDecision preHandle(AuthRequest request) {
        AuthResult result = authService.authenticate(request);
        if (result.isSuccess()) {
            return EntryDecision.allow(result.principal());
        }
        AuthErrorResponse response = errorResponseFactory.toResponse(
                result.failureCode(),
                request == null ? "" : request.traceId(),
                request == null ? "" : request.sessionId(),
                result.retryAfterSeconds());
        int statusCode = errorResponseFactory.httpStatus(result.failureCode());
        return EntryDecision.deny(statusCode, response);
    }

    public record EntryDecision(
            boolean allowed,
            int statusCode,
            AuthPrincipal principal,
            AuthErrorResponse errorResponse) {
        public static EntryDecision allow(AuthPrincipal principal) {
            return new EntryDecision(true, 200, principal, null);
        }

        public static EntryDecision deny(int statusCode, AuthErrorResponse errorResponse) {
            return new EntryDecision(false, statusCode, null, errorResponse);
        }
    }
}
