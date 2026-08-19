package com.aichat.service;

import com.aichat.dto.ChatRequest;
import com.aichat.model.ChatMessage;
import com.aichat.model.ChatSession;
import com.aichat.service.llm.LLMClient;
import com.aichat.service.llm.LLMClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 对话服务：管理多轮上下文，调用大模型并流式返回。 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_CONTEXT_MESSAGES = 16;
    private static final int MAX_CONTEXT_CHARS = 16000;
    private static final String SYSTEM_PROMPT = """
            你是一个严谨、简洁的技术助手。
            请使用清晰的标题、列表或代码块组织答案；每个要点只输出一次。
            不要重复已经表达过的句子、标题或段落。回答完成后立即停止。
            如果用户的问题包含重复内容，请先去重并按合理结构回答。
            """;

    private final SessionService sessionService;
    private final LLMClient llmClient;

    public ChatService(SessionService sessionService, LLMClientFactory factory) {
        this.sessionService = sessionService;
        this.llmClient = factory.create();
        log.info("当前对话引擎：{}", llmClient.isConfigured() ? "真实大模型 (OpenAI 兼容)" : "内置模拟引擎");
    }

    /** 流式对话，通过 SSE 逐 token 推送结果。 */
    public void streamChat(ChatRequest request, SseConnection connection) throws Exception {
        ChatSession session = sessionService.getOrCreate(request.sessionId());

        if (session.isEmpty() || "新会话".equals(session.getTitle())) {
            session.setTitle(truncate(request.message().trim(), 20));
        }

        session.appendMessage(new ChatMessage("user", request.message().trim(), System.currentTimeMillis()));
        List<ChatMessage> history = buildContext(session);

        StringBuilder fullReply = new StringBuilder();
        llmClient.streamChat(history, token -> {
            if (connection.isClosed()) {
                throw new SseConnection.ClientDisconnectedException();
            }
            fullReply.append(token);
            connection.send(Map.of("type", "token", "content", token));
        });

        String reply = fullReply.toString();
        if (ResponseQualityGuard.isLikelyRepetitive(reply)) {
            log.warn("Model response appears repetitive: sessionId={}, length={}",
                    request.sessionId(), reply.length());
        }

        session.appendMessage(new ChatMessage("assistant", reply, System.currentTimeMillis()));
        connection.send(Map.of("type", "done", "content", reply));
        connection.complete();
    }

    private List<ChatMessage> buildContext(ChatSession session) {
        List<ChatMessage> recent = session.getRecentMessages(MAX_CONTEXT_MESSAGES);
        List<ChatMessage> selected = new ArrayList<>();
        int chars = 0;

        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessage message = recent.get(i);
            int messageChars = message.content() == null ? 0 : message.content().length();
            if (!selected.isEmpty() && chars + messageChars > MAX_CONTEXT_CHARS) {
                break;
            }
            selected.add(0, message);
            chars += messageChars;
        }

        selected.add(0, new ChatMessage("system", SYSTEM_PROMPT, 0L));
        return selected;
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "…";
    }
}
