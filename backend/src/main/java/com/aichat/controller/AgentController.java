package com.aichat.controller;

import ch.qos.logback.core.util.StringUtil;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.aichat.core.service.AgentOrchestrator;
import com.aichat.exception.ClientAbortException;
import com.aichat.guard.RateLimiter;
import com.aichat.model.AgentContext;
import com.aichat.model.ChatMessage;
import com.aichat.model.ChatSession;
import com.aichat.service.SessionService;
import com.aichat.service.WorkspaceService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/agent")
@SaCheckLogin
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentOrchestrator orchestrator;
    private final RateLimiter limiter;
    private final SessionService sessionService;
    private final WorkspaceService workspaceService;
    private final Executor executor;

    public AgentController(AgentOrchestrator orchestrator, RateLimiter limiter,
                           SessionService sessionService, WorkspaceService workspaceService,
                           @Qualifier("chatExecutor") Executor executor) {
        this.orchestrator = orchestrator;
        this.limiter = limiter;
        this.sessionService = sessionService;
        this.workspaceService = workspaceService;
        this.executor = executor;
    }

    public record AgentChatRequest(String sessionId, @NotBlank(message = "消息不能为空") String message) {
    }

    /**
     * 流式 Agent 对话（SSE）
     * 事件数据格式：
     *   {"type":"token","content":"..."}
     *   {"type":"tool_call","name":"...","arguments":"..."}
     *   {"type":"tool_result","name":"...","content":"..."}
     *   {"type":"done","content":"完整回复","sessionId":"..."}
     *   {"type":"error","message":"..."}
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Validated @RequestBody AgentChatRequest req) {
        SseEmitter emitter = new SseEmitter(0L);

        // ⚠️ Sa-Token 上下文绑定在请求线程（ThreadLocal），必须在 executor.execute() 之前、
        // 还在 Web 上下文中时获取用户信息与工作区授权，再传入子线程，否则子线程拿不到 HttpServletRequest。
        final long userId = StpUtil.getLoginIdAsLong();
        final String userToken = StpUtil.getTokenValue();
        final String sid = (StringUtil.isNullOrEmpty(req.sessionId()))
                ? ("a" + System.currentTimeMillis() + "-" + userId)
                : req.sessionId();
        final String workspacePath = workspaceService.getAuthorizedWorkspacePath();

        executor.execute(() -> {
            try {
                emitter.onCompletion(() -> log.info("Agent SSE 生命周期结束"));
                emitter.onTimeout(() -> log.warn("Agent SSE 超时"));
                emitter.onError((Throwable t) -> log.warn("Agent SSE 出错或前端断开: {}",
                        t != null ? t.getMessage() : "null"));

                if (!limiter.tryAcquire("agent:" + userId)) {
                    sendEvent(emitter, "error", Map.of("type", "error", "message", "请求过于频繁，请稍后再试"));
                    emitter.complete();
                    return;
                }

                AgentContext ctx = new AgentContext(userId, sid, workspacePath, userToken);

                ChatSession session = sessionService.getOrCreate(sid);
                if (session.isEmpty() || "新会话".equals(session.getTitle())) {
                    sessionService.updateTitle(sid, truncate(req.message().trim(), 20));
                }

                // 先执行 Agent 流式生成（成功才落库，失败/中止时数据库无残留，前端刷新后不会看到孤立消息）
                String answer = orchestrator.streamChat(req.message(), ctx, event -> {
                    String type = (String) event.get("type");
                    if ("done".equals(type)) {
                        event.put("sessionId", sid);
                    }
                    sendEvent(emitter, "message", event);
                });

                // 生成成功后，一次性持久化完整对话（user + assistant）
                long now = System.currentTimeMillis();
                sessionService.appendMessage(sid, new ChatMessage("user", req.message().trim(), now));
                if (answer != null && !answer.isEmpty()) {
                    sessionService.appendMessage(sid, new ChatMessage("assistant", answer, System.currentTimeMillis()));
                }
                sendEvent(emitter, "message", Map.of("type", "done", "content", answer, "sessionId", sid));
                emitter.complete();

            } catch (ClientAbortException e) {
                log.info("前端主动中止了 Agent 生成，停止流式推送");
                // 前端连接已断开，无需再发送任何事件；由容器自动清理异步请求
                return;

            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if ("CLIENT_ABORT".equals(errorMsg) || (errorMsg != null && errorMsg.contains("CLIENT_ABORT"))) {
                    log.info("前端主动中止了 Agent 生成");
                    return;
                }
                log.error("Agent 流式生成异常", e);
                try {
                    sendEvent(emitter, "error", Map.of("type", "error",
                            "message", errorMsg != null ? errorMsg : "服务器内部错误"));
                } catch (Exception ignored) {}
                emitter.complete();
            }
        });

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            throw new ClientAbortException();
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "…";
    }
}
