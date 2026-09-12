package com.aichat.memory;

import com.aichat.model.AgentContext;
import com.aichat.model.AgentMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MemoryManager {

    private final AgentMemoryStore store;

    public MemoryManager(AgentMemoryStore store) {
        this.store = store;
    }

    public List<AgentMessage> load(AgentContext ctx) {
        return store.load(ctx.userId(), ctx.sessionId());
    }

    /**
     * 演进点：接RAG 用户画像后，这里返回注入的长期上下文
     * @param ctx 上下文
     * @return 用户画像长期上下文信息
     */
    public String loadProfile(AgentContext ctx) {
        return null;
    }

    public void save(AgentContext ctx, List<AgentMessage> messages) {
        store.save(ctx.userId(), ctx.sessionId(), messages);
    }
}
