package com.aichat.dto;

import jakarta.validation.constraints.NotBlank;

/** Chat request, optionally enabling the server-side phased task workflow. */
public record ChatRequest(
        String sessionId,
        @NotBlank(message = "Message cannot be blank") String message,
        Boolean taskMode
) {

    public boolean isTaskMode() {
        return Boolean.TRUE.equals(taskMode);
    }
}
