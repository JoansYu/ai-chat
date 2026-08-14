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

import java.io.IOException;
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

        // 首个用户消息自动生成会话标题
        if (session.isEmpty() || "新会话".equals(session.getTitle())) {
            session.setTitle(truncate(request.message().trim(), 20));
        }

        // 保存用户消息
        session.appendMessage(new ChatMessage("user", request.message().trim(), System.currentTimeMillis()));

        // 取最近 N 条作为多轮上下文
        List<ChatMessage> history = session.getRecentMessages(MAX_CONTEXT_MESSAGES);

        // 流式调用大模型，逐 token 转发
        StringBuilder fullReply = new StringBuilder();
        llmClient.streamChat(history, token -> {
            fullReply.append(token);
            send(emitter, Map.of("type", "token", "content", token));
        });

        // 保存助手回复
        String reply = fullReply.toString();
        session.appendMessage(new ChatMessage("assistant", reply, System.currentTimeMillis()));

        // 通知前端完成
        send(emitter, Map.of("type", "done", "content", reply));
        emitter.complete();
    }

    private void send(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event().name("message").data(data));
        } catch (IOException e) {
            throw new RuntimeException("SSE 事件发送失败", e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "…";
    }
}
