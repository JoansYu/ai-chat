package com.aichat.service;

import com.aichat.exception.NotFoundException;
import com.aichat.model.ChatSession;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 会话管理服务（内存存储）
 */
@Service
public class SessionService {

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();

    public ChatSession create() {
        String id = "s" + System.currentTimeMillis() + "-" + counter.incrementAndGet();
        ChatSession session = new ChatSession(id);
        sessions.put(id, session);
        return session;
    }

    public ChatSession get(String id) {
        ChatSession session = sessions.get(id);
        if (session == null) {
            throw new NotFoundException("会话不存在：" + id);
        }
        return session;
    }

    public List<ChatSession> list() {
        return sessions.values().stream()
                .sorted(Comparator.comparingLong(ChatSession::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public void delete(String id) {
        sessions.remove(id);
    }

    /**
     * 获取会话；ID 为空时自动新建，不存在时自动创建
     */
    public ChatSession getOrCreate(String id) {
        if (id == null || id.isBlank()) {
            return create();
        }
        return sessions.computeIfAbsent(id, k -> new ChatSession(k));
    }
}
