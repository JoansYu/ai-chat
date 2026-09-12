package com.aichat.model;

import java.util.List;

public record AgentMessage(
        String role,
        String content,
        List<ToolCall> toolCalls,
        String toolCallId
) {
    public static AgentMessage system(String c) {
        return new AgentMessage("system", c, null, null);
    }

    public static AgentMessage user(String c) {
        return new AgentMessage("user", c, null, null);
    }

    public static AgentMessage assistant(String c, List<ToolCall> t) {
        return new AgentMessage("assistant", c, t, null);
    }

    public static AgentMessage tool(String id, String c) {
        return new AgentMessage("tool", c, null, id);
    }
}
