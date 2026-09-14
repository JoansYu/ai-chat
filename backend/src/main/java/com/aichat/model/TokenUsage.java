package com.aichat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenUsage(
        @JsonProperty("prompt_tokens")
        int promptTokens,

        @JsonProperty("completion_tokens")
        int completionTokens,

        @JsonProperty("total_tokens")
        int totalTokens
) {

    public TokenUsage empty() {
        return new TokenUsage(0, 0, 0);
    }
}
