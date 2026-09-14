package com.aichat.core.service;


import com.aichat.core.Planner;
import com.aichat.core.client.AgentPythonClient;
import com.aichat.core.enums.IntentType;
import com.aichat.dto.AgentPythonRequest;
import com.aichat.dto.AgentPythonResponse;
import com.aichat.exception.ClientAbortException;
import com.aichat.memory.MemoryManager;
import com.aichat.model.AgentContext;
import com.aichat.model.AgentMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 总装，任务编排
 */
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final IntentClassifier intentClassifier;

    private final Planner planner;

    private final AgentPythonClient client;

    private final MemoryManager memory;

    public String chat(String userInput, AgentContext ctx) {
        List<AgentMessage> messages = memory.load(ctx);

        IntentType intent = intentClassifier.analyze(userInput, messages);
        System.out.println("🎯 [AgentOrchestrator] 识别到当前意图: " + intent);

        try {
            String answer;

            if (intent == IntentType.CHAT) {
                messages.add(AgentMessage.user(userInput));
            }
            else if (intent == IntentType.TASK_NEW || intent == IntentType.UNKNOWN || CollectionUtils.isEmpty(messages)) {
                String plan = planner.generatePlan(userInput);
                System.out.println("📝 [Planner] 生成的全局计划:\n" + plan);
                messages = planner.buildExecutionMessage(userInput, plan, ctx, memory.loadProfile(ctx));
            }
            else {
                messages.add(AgentMessage.user(userInput));
            }

            AgentPythonResponse response = client.executeAgent(AgentPythonRequest.of(messages), ctx.workspacePath(), ctx.userToken());

            if (!"success".equals(response.status())) {
                throw new RuntimeException("Python Agent 执行异常: " + response.errorMessage());
            }

            messages = response.messages();
            answer = response.finalAnswer();

            if (response.usage() != null) {
                System.out.println("📊 本次 Agent 节点 Token 消耗: " + response.usage().totalTokens());
            }

            memory.save(ctx, messages);
            return answer;

        } catch (Exception e) {
            throw new RuntimeException("Agent 调度执行异常", e);
        }
    }

    /**
     * 流式 Agent 对话
     *
     * @param userInput 用户输入
     * @param ctx       上下文
     * @param onEvent   SSE 事件回调，参数为事件 Map（含 type/content/name/arguments 等）
     * @return 完整回复文本
     */
    public String streamChat(String userInput, AgentContext ctx, Consumer<Map<String, Object>> onEvent) {
        List<AgentMessage> messages = memory.load(ctx);

        IntentType intent = intentClassifier.analyze(userInput, messages);
        System.out.println("🎯 [AgentOrchestrator-Stream] 识别到当前意图: " + intent);

        try {
            if (intent == IntentType.CHAT) {
                messages.add(AgentMessage.user(userInput));
            } else if (intent == IntentType.TASK_NEW || intent == IntentType.UNKNOWN || CollectionUtils.isEmpty(messages)) {
                String plan = planner.generatePlan(userInput);
                System.out.println("📝 [Planner] 生成的全局计划:\n" + plan);
                messages = planner.buildExecutionMessage(userInput, plan, ctx, memory.loadProfile(ctx));
            } else {
                messages.add(AgentMessage.user(userInput));
            }

            final StringBuilder fullAnswer = new StringBuilder();
            final List<AgentMessage> finalMessages = messages;

            client.streamExecuteAgent(AgentPythonRequest.of(messages), ctx.workspacePath(), ctx.userToken(), event -> {
                String type = (String) event.get("type");
                if ("token".equals(type)) {
                    fullAnswer.append(event.get("content"));
                }
                onEvent.accept(event);

                if ("done".equals(type)) {
                    Object msgs = event.get("messages");
                    if (msgs instanceof List<?> list && !list.isEmpty()) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                            finalMessages.clear();
                            for (Object item : list) {
                                finalMessages.add(om.convertValue(item, AgentMessage.class));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            });

            memory.save(ctx, finalMessages);
            return fullAnswer.toString();

        } catch (ClientAbortException e) {
            // 前端已断开连接，原样透传，禁止包装
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Agent 流式调度执行异常", e);
        }
    }


}
