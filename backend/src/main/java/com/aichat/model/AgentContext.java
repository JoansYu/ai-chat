package com.aichat.model;

public record AgentContext(
        Long userId,
        String sessionId,
        String workspacePath,
        String userToken,
        String clientId
) {

    public AgentContext(Long userId, String sessionId, String workspacePath, String userToken) {
        this(userId, sessionId, workspacePath, userToken, null);
    }

    public AgentContext(Long userId, String sessionId) {
        this(userId, sessionId, null, null, null);
    }

    public AgentContext(Long userId, String sessionId, String workspacePath) {
        this(userId, sessionId, workspacePath, null, null);
    }
}
