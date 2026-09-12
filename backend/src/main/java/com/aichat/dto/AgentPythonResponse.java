package com.aichat.dto;

import com.aichat.model.AgentMessage;
import com.aichat.model.TokenUsage;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AgentPythonResponse(

        @JsonProperty("status")
        String status,

        @JsonProperty("final_answer")
        String finalAnswer,

        @JsonProperty("messages")
        List<AgentMessage> messages,

        @JsonProperty("usage")
        TokenUsage usage,

        @JsonProperty("error_message")
        String errorMessage
) {
}
