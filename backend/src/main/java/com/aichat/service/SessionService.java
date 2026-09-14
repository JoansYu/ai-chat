package com.aichat.service;

import com.aichat.entity.ChatMessageEntity;
import com.aichat.entity.ChatSessionEntity;
import com.aichat.exception.NotFoundException;
import com.aichat.mapper.ChatMessageMapper;
import com.aichat.mapper.ChatSessionMapper;
import com.aichat.model.ChatMessage;
import com.aichat.model.ChatSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话管理服务（MySQL 持久化）
 */
@Service
public class SessionService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AtomicLong counter = new AtomicLong();

    public SessionService(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    public ChatSession create() {
        String id = "s" + System.currentTimeMillis() + "-" + counter.incrementAndGet();
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(id);
        entity.setTitle("新会话");
        entity.setCreatedAt(System.currentTimeMillis());
        sessionMapper.insert(entity);
        return new ChatSession(id, "新会话", entity.getCreatedAt(), new ArrayList<>());
    }

    public ChatSession get(String id) {
        ChatSessionEntity entity = sessionMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("会话不存在：" + id);
        }
        List<ChatMessageEntity> msgEntities = messageMapper.selectBySessionId(id);
        List<ChatMessage> messages = msgEntities.stream()
                .map(e -> new ChatMessage(e.getRole(), e.getContent(), e.getTimestamp()))
                .toList();
        return new ChatSession(id, entity.getTitle(), entity.getCreatedAt(), messages);
    }

    public ChatSession getOrCreate(String id) {
        if (id == null || id.isBlank()) {
            return create();
        }
        ChatSessionEntity entity = sessionMapper.selectById(id);
        if (entity == null) {
            entity = new ChatSessionEntity();
            entity.setId(id);
            entity.setTitle("新会话");
            entity.setCreatedAt(System.currentTimeMillis());
            sessionMapper.insert(entity);
            return new ChatSession(id, "新会话", entity.getCreatedAt(), new ArrayList<>());
        }
        return get(id);
    }

    public List<ChatSession> list() {
        List<ChatSessionEntity> entities = sessionMapper.selectAllOrderByCreatedDesc();
        return entities.stream()
                .map(e -> {
                    int count = messageMapper.countBySessionId(e.getId());
                    return new ChatSession(e.getId(), e.getTitle(), e.getCreatedAt(), count);
                })
                .toList();
    }

    @Transactional
    public void delete(String id) {
        messageMapper.deleteBySessionId(id);
        sessionMapper.deleteById(id);
    }

    public void updateTitle(String id, String title) {
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(id);
        entity.setTitle(title);
        sessionMapper.updateById(entity);
    }

    public void appendMessage(String sessionId, ChatMessage message) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole(message.role());
        entity.setContent(message.content());
        entity.setTimestamp(message.timestamp());
        messageMapper.insert(entity);
    }

    public List<ChatMessage> getRecentMessages(String sessionId, int n) {
        List<ChatMessageEntity> entities = messageMapper.selectRecentBySessionId(sessionId, n);
        List<ChatMessage> messages = entities.stream()
                .map(e -> new ChatMessage(e.getRole(), e.getContent(), e.getTimestamp()))
                .toList();
        List<ChatMessage> reversed = new ArrayList<>(messages);
        Collections.reverse(reversed);
        return reversed;
    }
}
