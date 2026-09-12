package com.aichat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionChunk(
        String id,
        String object,
        String created,
        String model,
        @JsonProperty(value = "system_fingerprint")
        String systemFingerprint,
        List<Choice> choices

) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            String index,
            Delta delta,
            @JsonProperty(value = "finish_reason")
            String finishReason
    ){
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Delta(
                String role,
                String content,
                String reasoning
        ){}
    }
}
