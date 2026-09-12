package com.aichat.memory;

import com.aichat.model.AgentMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentMemoryStore {

    private final Map<Long, Map<String, List<AgentMessage>>> store = new ConcurrentHashMap<>();

    private final int maxMessages;

    public AgentMemoryStore(@Value("${agent.memory.max-messages:24}")int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public List<AgentMessage> load(Long userId, String sessionId) {
        Map<String, List<AgentMessage>> userSessions = store.get(userId);
        if (userSessions == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(userSessions.getOrDefault(sessionId, new ArrayList<>()));
    }

    public void save(Long userId, String sessionId, List<AgentMessage> messages) {
        Map<String, List<AgentMessage>> userSessions =
                store.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        List<AgentMessage> trimmed = messages.size() > maxMessages
                ? new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()))
                : new ArrayList<>(messages);
        userSessions.put(sessionId, trimmed);
    }
}
