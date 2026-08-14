package com.aichat.controller;

import com.aichat.model.ChatSession;
import com.aichat.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理接口
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** 创建新会话 */
    @PostMapping
    public Map<String, Object> create() {
        ChatSession session = sessionService.create();
        return toMap(session);
    }

    /** 会话列表 */
    @GetMapping
    public List<Map<String, Object>> list() {
        return sessionService.list().stream().map(this::toMap).toList();
    }

    /** 会话历史消息 */
    @GetMapping("/{id}/messages")
    public List<?> messages(@PathVariable String id) {
        return sessionService.get(id).getMessages();
    }

    /** 删除会话 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        sessionService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private Map<String, Object> toMap(ChatSession session) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", session.getId());
        map.put("title", session.getTitle());
        map.put("createdAt", session.getCreatedAt());
        map.put("messageCount", session.getMessageCount());
        return map;
    }
}
