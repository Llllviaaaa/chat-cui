package com.chatcui.gateway.http;

import com.chatcui.gateway.auth.AuthService;
import com.chatcui.gateway.auth.ErrorResponseFactory;
import com.chatcui.gateway.auth.model.AuthErrorResponse;
import com.chatcui.gateway.auth.model.AuthPrincipal;
import com.chatcui.gateway.auth.model.AuthRequest;
import com.chatcui.gateway.auth.model.AuthResult;
import com.chatcui.gateway.observability.BridgeMetricsRegistry;

public class AuthEntryInterceptor {
    private final AuthService authService;
    private final ErrorResponseFactory errorResponseFactory;
    private final BridgeMetricsRegistry metricsRegistry;

    public AuthEntryInterceptor(AuthService authService, ErrorResponseFactory errorResponseFactory) {
        this(authService, errorResponseFactory, BridgeMetricsRegistry.noop());
    }

    public AuthEntryInterceptor(
            AuthService authService,
            ErrorResponseFactory errorResponseFactory,
            BridgeMetricsRegistry metricsRegistry) {
        this.authService = authService;
        this.errorResponseFactory = errorResponseFactory;
        this.metricsRegistry = metricsRegistry == null ? BridgeMetricsRegistry.noop() : metricsRegistry;
    }

    public EntryDecision preHandle(AuthRequest request) {
        AuthResult result = authService.authenticate(request);
        if (result.isSuccess()) {
            return EntryDecision.allow(result.principal());
        }
        metricsRegistry.recordAuthFailure(result.failureCode());
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
