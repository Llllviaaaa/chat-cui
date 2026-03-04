package com.chatcui.gateway.ws;

import com.chatcui.gateway.auth.AuthService;
import com.chatcui.gateway.auth.ErrorResponseFactory;
import com.chatcui.gateway.auth.model.AuthErrorResponse;
import com.chatcui.gateway.auth.model.AuthPrincipal;
import com.chatcui.gateway.auth.model.AuthRequest;
import com.chatcui.gateway.auth.model.AuthResult;
import com.chatcui.gateway.observability.BridgeMetricsRegistry;

public class WsAuthHandshakeInterceptor {
    private final AuthService authService;
    private final ErrorResponseFactory errorResponseFactory;
    private final BridgeMetricsRegistry metricsRegistry;

    public WsAuthHandshakeInterceptor(AuthService authService, ErrorResponseFactory errorResponseFactory) {
        this(authService, errorResponseFactory, BridgeMetricsRegistry.noop());
    }

    public WsAuthHandshakeInterceptor(
            AuthService authService,
            ErrorResponseFactory errorResponseFactory,
            BridgeMetricsRegistry metricsRegistry) {
        this.authService = authService;
        this.errorResponseFactory = errorResponseFactory;
        this.metricsRegistry = metricsRegistry == null ? BridgeMetricsRegistry.noop() : metricsRegistry;
    }

    public HandshakeDecision beforeHandshake(AuthRequest request) {
        AuthResult result = authService.authenticate(request);
        if (result.isSuccess()) {
            return HandshakeDecision.accept(result.principal());
        }
        metricsRegistry.recordAuthFailure(result.failureCode());
        AuthErrorResponse response = errorResponseFactory.toResponse(
                result.failureCode(),
                request == null ? "" : request.traceId(),
                request == null ? "" : request.sessionId(),
                result.retryAfterSeconds());
        return HandshakeDecision.reject(errorResponseFactory.wsCloseCode(result.failureCode()), response);
    }

    public record HandshakeDecision(
            boolean accepted,
            int closeCode,
            AuthPrincipal principal,
            AuthErrorResponse errorResponse) {
        public static HandshakeDecision accept(AuthPrincipal principal) {
            return new HandshakeDecision(true, 0, principal, null);
        }

        public static HandshakeDecision reject(int closeCode, AuthErrorResponse response) {
            return new HandshakeDecision(false, closeCode, null, response);
        }
    }
}
