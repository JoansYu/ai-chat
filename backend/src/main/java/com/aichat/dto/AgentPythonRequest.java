package com.aichat.dto;

import com.aichat.model.AgentMessage;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Python Agent 请求体
 * 重构后：Java 不再做意图识别和规划，只传 userInput + history，Python 内部完成
 */
public record AgentPythonRequest(
        @JsonProperty("user_input")
        String userInput,

        @JsonProperty("history")
        List<AgentMessage> history,

        @JsonProperty("max_steps")
        int maxSteps
) {

    public static AgentPythonRequest of(String userInput, List<AgentMessage> history) {
        return new AgentPythonRequest(userInput, history, 10);
    }
}
