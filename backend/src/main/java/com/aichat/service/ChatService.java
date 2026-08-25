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
import java.util.concurrent.atomic.AtomicInteger;

/** Coordinates normal chat and the optional phased task workflow. */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_CONTEXT_MESSAGES = 16;
    private static final int MAX_CONTEXT_CHARS = 16000;
    private static final String SYSTEM_PROMPT = """
            You are a rigorous and concise technical assistant. Use clear headings, lists, and code blocks.
            Output only user-visible conclusions, plans, and code. Do not output hidden chain-of-thought.
            For large tasks, respect the current phase and do not repeat completed work.
            """;

    private final SessionService sessionService;
    private final LLMClient llmClient;

    public ChatService(SessionService sessionService, LLMClientFactory factory) {
        this.sessionService = sessionService;
        this.llmClient = factory.create();
        log.info("Chat engine configured: {}", llmClient.isConfigured() ? "remote model" : "mock engine");
    }

    public void streamChat(ChatRequest request, SseConnection connection) throws Exception {
        long startedAt = System.nanoTime();
        ChatSession session = sessionService.getOrCreate(request.sessionId());
        if (session.isEmpty() || "New session".equals(session.getTitle())) {
            session.setTitle(truncate(request.message().trim(), 20));
        }

        session.appendMessage(new ChatMessage("user", request.message().trim(), System.currentTimeMillis()));
        List<String> plan = request.isTaskMode() ? buildPlan(request.message()) : List.of("Answer the request");
        connection.send(Map.of("type", "plan", "requestId", connection.requestId(),
                "steps", plan, "total", plan.size()));

        StringBuilder fullReply = new StringBuilder();
        List<String> completedSteps = new ArrayList<>();
        AtomicInteger reasoningChunks = new AtomicInteger();

        for (int index = 0; index < plan.size(); index++) {
            if (connection.isClosed()) {
                throw new SseConnection.ClientDisconnectedException();
            }
            String stepTitle = plan.get(index);
            int stepNumber = index + 1;
            connection.send(Map.of("type", "step_started", "step", stepNumber,
                    "total", plan.size(), "title", stepTitle));
            connection.sendStatus("thinking", "Analyzing step " + stepNumber + "/" + plan.size() + ": " + stepTitle);

            List<ChatMessage> context = request.isTaskMode()
                    ? buildStepContext(request.message(), completedSteps, stepNumber, plan.size(), stepTitle)
                    : buildContext(session);
            StringBuilder stepReply = new StringBuilder();
            long stepStartedAt = System.nanoTime();
            llmClient.streamChat(context, token -> {
                if (connection.isClosed()) {
                    throw new SseConnection.ClientDisconnectedException();
                }
                if (stepReply.isEmpty()) {
                    connection.sendStatus("generating", "Generating step " + stepNumber);
                }
                stepReply.append(token);
                fullReply.append(token);
                connection.send(Map.of("type", "token", "content", token));
            }, ignored -> {
                int count = reasoningChunks.incrementAndGet();
                if (count == 1 || count % 8 == 0) {
                    connection.sendStatus("thinking", "Model is analyzing (" + count + " reasoning chunks)");
                }
            });

            completedSteps.add(stepReply.toString());
            connection.send(Map.of("type", "step_done", "step", stepNumber,
                    "total", plan.size(), "title", stepTitle, "chars", stepReply.length(),
                    "elapsedMs", elapsedMs(stepStartedAt)));
        }

        String reply = fullReply.toString();
        session.appendMessage(new ChatMessage("assistant", reply, System.currentTimeMillis()));
        connection.send(Map.of("type", "done", "content", reply,
                "elapsedMs", elapsedMs(startedAt), "requestId", connection.requestId()));
        connection.complete();
        log.info("Chat completed: requestId={}, steps={}, chars={}, elapsedMs={}",
                connection.requestId(), plan.size(), reply.length(), elapsedMs(startedAt));
    }

    private List<String> buildPlan(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (text.contains("code") || text.contains("system") || text.contains("implement")
                || text.contains("front") || text.contains("backend") || text.contains("rbac")
                || text.contains("代码") || text.contains("系统") || text.contains("实现")) {
            return List.of("Requirements and architecture", "Authentication and RBAC backend",
                    "Employee APIs and data model", "Frontend pages and route permissions",
                    "Tests, startup, and delivery notes");
        }
        return List.of("Analyze requirements", "Organize the answer", "Check omissions");
    }

    private List<ChatMessage> buildStepContext(String request, List<String> completed,
                                               int step, int total, String title) {
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT)
                .append("\nThis is a phased task. Execute step ").append(step).append("/").append(total)
                .append(": ").append(title).append(". Complete only this step.")
                .append("\nOriginal request:\n").append(request);
        if (!completed.isEmpty()) {
            prompt.append("\n\nCompleted outputs from earlier steps:\n");
            for (int i = 0; i < completed.size(); i++) {
                prompt.append("Step ").append(i + 1).append(":\n")
                        .append(truncate(completed.get(i), 5000)).append("\n");
            }
        }
        return List.of(new ChatMessage("system", prompt.toString(), 0L),
                new ChatMessage("user", "Produce the deliverable for the current step.", System.currentTimeMillis()));
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

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }
}
