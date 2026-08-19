package com.aichat.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.aichat.dto.ChatRequest;
import com.aichat.service.ChatService;
import com.aichat.service.SseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Chat endpoints. */
@RestController
@RequestMapping("/api/chat")
@SaCheckLogin
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Validated @RequestBody ChatRequest request) {
        SseConnection connection = new SseConnection(0L);
        executor.execute(() -> {
            try {
                chatService.streamChat(request, connection);
            } catch (SseConnection.ClientDisconnectedException e) {
                // A browser or proxy closing an SSE stream is expected and is
                // not a server failure. The connection callbacks already mark
                // the emitter as closed.
                log.debug("SSE client disconnected before the response completed");
            } catch (Exception e) {
                if (connection.isClosed()) {
                    log.debug("SSE response was closed before the server error could be sent", e);
                } else {
                    log.error("SSE stream failed", e);
                    connection.sendError(e);
                }
            }
        });
        return connection.emitter();
    }
}
