package com.aichat.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 对话会话（内存存储），保存多轮消息历史
 */
public class ChatSession {

    private final String id;
    private String title;
    private final long createdAt;
    private final CopyOnWriteArrayList<ChatMessage> messages = new CopyOnWriteArrayList<>();

    public ChatSession(String id) {
        this.id = id;
        this.title = "新会话";
        this.createdAt = System.currentTimeMillis();
    }

    public void appendMessage(ChatMessage message) {
        messages.add(message);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getMessageCount() {
        return messages.size();
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    /**
     * 获取最近 N 条消息，用于作为多轮上下文发送给 LLM
     */
    public List<ChatMessage> getRecentMessages(int n) {
        int size = messages.size();
        int from = Math.max(0, size - n);
        return Collections.unmodifiableList(new ArrayList<>(messages.subList(from, size)));
    }
}
