package com.aichat.controller;

import com.aichat.dto.ChatRequest;
import com.aichat.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话接口
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 流式对话（SSE）
     * 事件数据格式：{"type":"token","content":"..."} / {"type":"done","content":"完整回复"} / {"type":"error","message":"..."}
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Validated @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        executor.execute(() -> {
            try {
                chatService.streamChat(request, emitter);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("type", "error", "message", e.getMessage() == null ? "服务异常" : e.getMessage())));
                } catch (IOException ignored) {
                    // ignore
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
