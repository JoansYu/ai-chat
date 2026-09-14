package com.aichat.model;

public record AgentContext(
        Long userId,
        String sessionId,
        String workspacePath,
        String userToken
) {

    public AgentContext(Long userId, String sessionId) {
        this(userId, sessionId, null, null);
    }

    public AgentContext(Long userId, String sessionId, String workspacePath) {
        this(userId, sessionId, workspacePath, null);
    }
}
