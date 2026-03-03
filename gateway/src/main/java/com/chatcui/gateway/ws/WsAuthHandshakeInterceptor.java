package com.chatcui.gateway.ws;

import com.chatcui.gateway.auth.AuthService;
import com.chatcui.gateway.auth.ErrorResponseFactory;
import com.chatcui.gateway.auth.model.AuthErrorResponse;
import com.chatcui.gateway.auth.model.AuthPrincipal;
import com.chatcui.gateway.auth.model.AuthRequest;
import com.chatcui.gateway.auth.model.AuthResult;

public class WsAuthHandshakeInterceptor {
    private final AuthService authService;
    private final ErrorResponseFactory errorResponseFactory;

    public WsAuthHandshakeInterceptor(AuthService authService, ErrorResponseFactory errorResponseFactory) {
        this.authService = authService;
        this.errorResponseFactory = errorResponseFactory;
    }

    public HandshakeDecision beforeHandshake(AuthRequest request) {
        AuthResult result = authService.authenticate(request);
        if (result.isSuccess()) {
            return HandshakeDecision.accept(result.principal());
        }
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
