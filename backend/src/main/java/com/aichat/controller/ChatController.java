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
import jakarta.servlet.http.HttpServletResponse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Chat endpoints. */
@RestController
@RequestMapping("/api/chat")
@SaCheckLogin
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final ScheduledExecutorService HEARTBEATS = Executors.newScheduledThreadPool(1);

    private final ChatService chatService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Validated @RequestBody ChatRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        SseConnection connection = new SseConnection(SSE_TIMEOUT_MS);
        ScheduledFuture<?> heartbeat = HEARTBEATS.scheduleAtFixedRate(() -> {
            if (connection.isClosed()) {
                return;
            }
            try {
                connection.sendHeartbeat();
            } catch (SseConnection.ClientDisconnectedException ignored) {
                log.debug("SSE heartbeat stopped: requestId={}", connection.requestId());
            }
        }, 10, 10, TimeUnit.SECONDS);
        Future<?> task = executor.submit(() -> {
            try {
                log.info("SSE request started: requestId={}, sessionId={}, taskMode={}",
                        connection.requestId(), request.sessionId(), request.isTaskMode());
                connection.sendStatus("accepted", "Request accepted by the chat server");
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
            } finally {
                heartbeat.cancel(false);
                log.info("SSE request finished: requestId={}, closed={}",
                        connection.requestId(), connection.isClosed());
            }
        });
        connection.onClose(() -> task.cancel(true));
        return connection.emitter();
    }
}
