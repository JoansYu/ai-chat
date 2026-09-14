package com.aichat.service;

import com.aichat.dto.ChatRequest;
import com.aichat.model.ChatMessage;
import com.aichat.model.ChatSession;
import com.aichat.service.llm.LLMClient;
import com.aichat.service.llm.LLMClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 对话服务：管理多轮上下文，调用大模型并流式返回
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_CONTEXT_MESSAGES = 24;

    private final SessionService sessionService;
    private final LLMClient llmClient;

    public ChatService(SessionService sessionService, LLMClientFactory factory) {
        this.sessionService = sessionService;
        this.llmClient = factory.create();
        log.info("当前对话引擎：{}", llmClient.isConfigured() ? "真实大模型 (OpenAI 兼容)" : "内置模拟引擎");
    }

    /**
     * 流式对话，通过 SSE 逐 token 推送结果
     */
    public void streamChat(ChatRequest request, SseEmitter emitter) throws Exception {
        ChatSession session = sessionService.getOrCreate(request.sessionId());

        if (session.isEmpty() || "新会话".equals(session.getTitle())) {
            sessionService.updateTitle(session.getId(), truncate(request.message().trim(), 20));
        }

        sessionService.appendMessage(session.getId(),
                new ChatMessage("user", request.message().trim(), System.currentTimeMillis()));

        List<ChatMessage> history = sessionService.getRecentMessages(session.getId(), MAX_CONTEXT_MESSAGES);

        StringBuilder fullReply = new StringBuilder();
        llmClient.streamChat(history, token -> {
            fullReply.append(token);
            send(emitter, Map.of("type", "token", "content", token));
        });

        String reply = fullReply.toString();
        sessionService.appendMessage(session.getId(),
                new ChatMessage("assistant", reply, System.currentTimeMillis()));

        send(emitter, Map.of("type", "done", "content", reply));
    }

    private void send(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event().name("message").data(data));
        } catch (Exception e) {
            throw new RuntimeException("CLIENT_ABORT");
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "…";
    }
}
