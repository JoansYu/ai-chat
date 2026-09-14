package com.aichat.memory;

import com.aichat.entity.AgentMessageEntity;
import com.aichat.mapper.AgentMessageMapper;
import com.aichat.model.AgentMessage;
import com.aichat.model.ToolCall;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AgentMemoryStore {

    private final AgentMessageMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxMessages;

    public AgentMemoryStore(AgentMessageMapper mapper,
                            @Value("${agent.memory.max-messages:24}") int maxMessages) {
        this.mapper = mapper;
        this.maxMessages = maxMessages;
    }

    public List<AgentMessage> load(Long userId, String sessionId) {
        List<AgentMessageEntity> entities = mapper.selectByUserAndSession(userId, sessionId);
        List<AgentMessage> messages = new ArrayList<>();
        for (AgentMessageEntity e : entities) {
            List<ToolCall> toolCalls = null;
            if (e.getToolCalls() != null && !e.getToolCalls().isEmpty()) {
                try {
                    toolCalls = objectMapper.readValue(e.getToolCalls(), new TypeReference<>() {});
                } catch (Exception ignored) {}
            }
            messages.add(new AgentMessage(e.getRole(), e.getContent(), toolCalls, e.getToolCallId(), null));
        }
        return messages;
    }

    public void save(Long userId, String sessionId, List<AgentMessage> messages) {
        mapper.deleteByUserAndSession(userId, sessionId);

        List<AgentMessage> toSave = messages.size() > maxMessages
                ? new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()))
                : new ArrayList<>(messages);

        for (int i = 0; i < toSave.size(); i++) {
            AgentMessage msg = toSave.get(i);
            AgentMessageEntity entity = new AgentMessageEntity();
            entity.setUserId(userId);
            entity.setSessionId(sessionId);
            entity.setRole(msg.role());
            entity.setContent(msg.content());
            entity.setToolCallId(msg.toolCallId());
            entity.setSeq(i);
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                try {
                    entity.setToolCalls(objectMapper.writeValueAsString(msg.toolCalls()));
                } catch (Exception ignored) {}
            }
            mapper.insert(entity);
        }
    }
}
