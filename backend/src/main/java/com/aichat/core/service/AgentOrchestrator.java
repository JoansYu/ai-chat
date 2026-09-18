package com.aichat.core.service;

import com.aichat.core.client.AgentPythonClient;
import com.aichat.dto.AgentPythonRequest;
import com.aichat.dto.AgentPythonResponse;
import com.aichat.exception.ClientAbortException;
import com.aichat.memory.MemoryManager;
import com.aichat.model.AgentContext;
import com.aichat.model.AgentMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent 编排服务（重构后：意图识别 + 规划已迁移到 Python，Java 只负责记忆加载 + 转发 + 记忆保存）
 */
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AgentPythonClient client;
    private final MemoryManager memory;

    public String chat(String userInput, AgentContext ctx) {
        List<AgentMessage> history = memory.load(ctx);

        try {
            AgentPythonResponse response = client.executeAgent(
                    AgentPythonRequest.of(userInput, history), ctx.workspacePath(), ctx.userToken());

            if (!"success".equals(response.status())) {
                String detail = response.errorMessage();
                if (detail == null || detail.isBlank()) {
                    detail = response.finalAnswer();
                }
                throw new RuntimeException("Python Agent 执行异常: " + detail);
            }

            String answer = response.finalAnswer();
            if (response.usage() != null) {
                System.out.println("📊 Token 消耗: " + response.usage().totalTokens());
            }

            // Java 自行构建历史（不依赖 Python 回传 messages，避免字段不匹配导致记忆丢失）
            List<AgentMessage> finalMessages = new ArrayList<>(history);
            finalMessages.add(AgentMessage.user(userInput));
            finalMessages.add(AgentMessage.assistant(answer, response.usage()));
            memory.save(ctx, finalMessages);
            return answer;

        } catch (Exception e) {
            throw new RuntimeException("Agent 调度执行异常", e);
        }
    }

    /**
     * 流式 Agent 对话
     * 重构后：Java 只加载历史 → 传给 Python（Python 内部做意图识别+规划+执行）→ 保存结果
     */
    public String streamChat(String userInput, AgentContext ctx, Consumer<Map<String, Object>> onEvent) {
        List<AgentMessage> history = memory.load(ctx);
        System.out.println("🎯 [Orchestrator-Stream] 用户输入: " + userInput);
        System.out.println("🎯 [Orchestrator-Stream] 历史消息数: " + history.size()
                + (history.isEmpty() ? " (无历史)" : ""));
        if (!history.isEmpty()) {
            for (AgentMessage m : history) {
                System.out.println("🎯 [Orchestrator-Stream]   历史[" + m.role() + "]: "
                        + (m.content() == null ? "" : m.content().substring(0, Math.min(60, m.content().length()))));
            }
        }

        try {
            final StringBuilder fullAnswer = new StringBuilder();

            client.streamExecuteAgent(
                    AgentPythonRequest.of(userInput, history),
                    ctx.workspacePath(), ctx.userToken(),
                    event -> {
                        String type = (String) event.get("type");
                        if ("token".equals(type)) {
                            fullAnswer.append(event.get("content"));
                        }
                        onEvent.accept(event);
                    });

            // Java 自行构建历史：history + 本轮 user + assistant 回复
            // （不依赖 Python 回传 messages 反序列化——Python 消息含 tool_call_id/name/tool_calls
            //   等字段与 AgentMessage 不匹配，曾导致历史保存为空、下一轮失忆）
            List<AgentMessage> finalMessages = new ArrayList<>(history);
            finalMessages.add(AgentMessage.user(userInput));
            finalMessages.add(AgentMessage.assistant(fullAnswer.toString(), null));
            memory.save(ctx, finalMessages);
            System.out.println("✅ [Orchestrator-Stream] 回复长度=" + fullAnswer.length()
                    + ", 已保存历史条数=" + finalMessages.size());

            return fullAnswer.toString();

        } catch (ClientAbortException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Agent 流式调度执行异常", e);
        }
    }
}
