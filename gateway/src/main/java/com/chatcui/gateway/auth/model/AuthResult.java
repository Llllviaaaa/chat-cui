package com.chatcui.gateway.auth.model;

public record AuthResult(
        AuthPrincipal principal,
        AuthFailureCode failureCode,
        Integer retryAfterSeconds
) {
    public static AuthResult success(AuthPrincipal principal) {
        return new AuthResult(principal, null, null);
    }

    public static AuthResult failure(AuthFailureCode failureCode, Integer retryAfterSeconds) {
        return new AuthResult(null, failureCode, retryAfterSeconds);
    }

    public boolean isSuccess() {
        return principal != null;
    }
}
