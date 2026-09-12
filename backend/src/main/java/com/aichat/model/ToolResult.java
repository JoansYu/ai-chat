package com.aichat.model;

public record ToolResult(
        String content
) {
    public static ToolResult ok(String c) {
        return new ToolResult(c);
    }

    public static ToolResult fail(String c) {
        return new ToolResult(c);
    }
}
