package com.aichat.model;

/**
 * 单条对话消息
 *
 * @param role     消息角色：user / assistant
 * @param content  消息内容
 * @param timestamp 消息时间戳（毫秒）
 */
public record ChatMessage(String role, String content, long timestamp) {
}
