package com.chatcui.gateway.runtime;

public record ResumeAnchor(String sessionId, String turnId, long seq) {
}
