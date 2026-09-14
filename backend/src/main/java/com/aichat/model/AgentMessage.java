package com.aichat.model;

import java.util.List;

public record AgentMessage(
        String role,
        String content,
        List<ToolCall> toolCalls,
        String toolCallId,
        TokenUsage usage
) {
    public static AgentMessage system(String c) {
        return new AgentMessage("system", c, null, null, null);
    }

    public static AgentMessage user(String c) {
        return new AgentMessage("user", c, null, null, null);
    }

    // 👇 新增：处理纯文本回复的情况（比如闲聊返回、最终答案返回）
    public static AgentMessage assistant(String c, TokenUsage usage) {
        return new AgentMessage("assistant", c, null, null, usage);
    }

    public static AgentMessage assistant(String c, List<ToolCall> t, TokenUsage usage) {
        return new AgentMessage("assistant", c, t, null, usage);
    }

    public static AgentMessage tool(String id, String c) {
        return new AgentMessage("tool", c, null, id, null);
    }
}
