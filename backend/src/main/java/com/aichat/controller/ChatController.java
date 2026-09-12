package com.aichat.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aichat.dto.ChatRequest;
import com.aichat.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话接口
 */
@RestController
@RequestMapping("/api/chat")
@SaCheckLogin
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final Executor executor;

    public ChatController(ChatService chatService, @Qualifier("chatExecutor")Executor executor) {
        this.chatService = chatService;
        this.executor = executor;
    }

    /**
     * 流式对话（SSE）
     * 事件数据格式：{"type":"token","content":"..."} / {"type":"done","content":"完整回复"} / {"type":"error","message":"..."}
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Validated @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L); // 建立连接
        executor.execute(() -> {
            try {
                // 1. 生命周期回调（只做日志记录，绝对不要在这里调用 complete()）
                emitter.onCompletion(() -> log.info("SSE 生命周期结束 (正常或异常终止)"));
                emitter.onTimeout(() -> log.warn("SSE 会话触发超时"));
                emitter.onError((Throwable t) -> log.warn("SSE 会话出错或前端主动断开: {}", t != null ? t.getMessage() : "null"));

                // 2. 执行真正的业务逻辑
                chatService.streamChat(request, emitter);

                // 3. 业务执行到底，代表大模型回答完毕，这时候才正常结束
                emitter.complete();

            } catch (Exception e) {
                String errorMsg = e.getMessage();

                if ("CLIENT_ABORT".equals(errorMsg) || (errorMsg != null && errorMsg.contains("CLIENT_ABORT"))) {
                    log.info("前端主动中止了生成，已安全中断大模型线程。");
                    emitter.complete();
                } else {
                    log.error("流式生成发生业务异常!", e);

                    try {
                        // 1. 通过 SSE 事件流，把错误发给前端
                        String msgToSend = (errorMsg != null) ? errorMsg : "服务器内部错误(请查看后端日志)";
                        emitter.send(SseEmitter.event().name("error")
                                .data(Map.of("type", "error", "message", msgToSend)));
                    } catch (Exception ignored) {}

                    // 👇 核心修复2：【绝对不要】调用 emitter.completeWithError(e) !
                    // 既然我们已经用上面的 send 发送了 error 事件，业务上就已经闭环了。
                    // 此时只需正常关闭水管，阻止 Spring 去调用 GlobalExceptionHandler。
                    emitter.complete();
                }
            }
        });
        return emitter;
    }
}
