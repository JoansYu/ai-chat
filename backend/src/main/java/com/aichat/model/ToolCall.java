package com.aichat.model;

public record ToolCall(
        String id,
        String name,
        String arguments
) {

}
