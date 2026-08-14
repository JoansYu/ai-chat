package com.aichat.service.llm;

import com.aichat.model.ChatMessage;

import java.util.List;
import java.util.function.Consumer;

/**
 * 大模型客户端抽象接口
 */
public interface LLMClient {

    /** 是否已配置真实大模型 */
    boolean isConfigured();

    /**
     * 非流式对话
     *
     * @param messages 多轮消息历史
     * @return 模型回复内容
     */
    String chat(List<ChatMessage> messages) throws Exception;

    /**
     * 流式对话，逐 token 回调
     *
     * @param messages 多轮消息历史
     * @param onToken  每个增量内容回调
     */
    void streamChat(List<ChatMessage> messages, Consumer<String> onToken) throws Exception;
}
