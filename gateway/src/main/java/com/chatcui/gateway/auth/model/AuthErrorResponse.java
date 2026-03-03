package com.chatcui.gateway.auth.model;

public record AuthErrorResponse(
        String error_code,
        String message,
        String next_action,
        Integer retry_after,
        String trace_id,
        String session_id,
        String debug_id
) {
}
