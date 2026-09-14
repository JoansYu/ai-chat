package com.aichat.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对话会话领域对象（不可变，数据从 DB 加载）
 */
public class ChatSession {

    private final String id;
    private final String title;
    private final long createdAt;
    private final int messageCount;
    private final List<ChatMessage> messages;

    public ChatSession(String id, String title, long createdAt, List<ChatMessage> messages) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.messageCount = this.messages.size();
    }

    public ChatSession(String id, String title, long createdAt, int messageCount) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.messageCount = messageCount;
        this.messages = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public boolean isEmpty() {
        return messageCount == 0;
    }

    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public List<ChatMessage> getRecentMessages(int n) {
        int size = messages.size();
        int from = Math.max(0, size - n);
        return Collections.unmodifiableList(new ArrayList<>(messages.subList(from, size)));
    }
}
