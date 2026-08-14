package com.aichat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 对话请求
 *
 * @param sessionId 会话 ID（为空时后端自动创建新会话）
 * @param message   用户输入的消息
 */
public record ChatRequest(
        String sessionId,
        @NotBlank(message = "消息内容不能为空") String message
) {
}
