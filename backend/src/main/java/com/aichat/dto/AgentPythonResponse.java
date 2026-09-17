package com.aichat.dto;

import com.aichat.model.TokenUsage;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record AgentPythonResponse(

        @JsonProperty("status")
        String status,

        @JsonProperty("final_answer")
        String finalAnswer,

        /**
         * Python 回传的完整消息列表（含 tool 调用链）。
         * 使用 Map 保留原始结构，避免因字段名不匹配导致的整体反序列化失败。
         * Java 侧构建多轮历史不再依赖此字段，仅作诊断/透传用途。
         */
        @JsonProperty("messages")
        List<Map<String, Object>> messages,

        @JsonProperty("usage")
        TokenUsage usage,

        @JsonProperty("error_message")
        String errorMessage
) {
}
