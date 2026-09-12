package com.aichat.dto;

import com.aichat.model.AgentMessage;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AgentPythonRequest(
        @JsonProperty("messages")
        List<AgentMessage> messages,

        @JsonProperty("max_steps")
        int maxSteps
) {

    public static AgentPythonRequest of(List<AgentMessage> messages) {
        return new AgentPythonRequest(messages, 5);
    }
}
