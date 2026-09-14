package com.aichat.websocket;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Agent 工具执行 WebSocket Handler（服务器端）
 * 按 userId 管理本地客户端连接，接收工具执行结果并完成 CompletableFuture
 */
@Component
public class AgentToolWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentToolWebSocketHandler.class);
    private final ObjectMapper om = new ObjectMapper();

    /** userId → WebSocket Session */
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    /** requestId → CompletableFuture（等待本地客户端返回工具执行结果） */
    private final Map<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = extractUserId(session);
        if (userId == null) {
            log.warn("WebSocket 连接未携带有效 token，关闭");
            try { session.close(CloseStatus.NOT_ACCEPTABLE); } catch (Exception ignored) {}
            return;
        }
        userSessions.put(userId, session);
        log.info("本地客户端已连接: userId={}, sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = om.readTree(message.getPayload());
            String requestId = node.path("requestId").asText();
            String result = node.path("result").asText("");
            boolean error = node.path("error").asBoolean(false);

            CompletableFuture<String> future = pendingRequests.remove(requestId);
            if (future != null) {
                if (error) {
                    future.complete("工具执行错误: " + result);
                } else {
                    future.complete(result);
                }
            }
        } catch (Exception e) {
            log.error("解析 WebSocket 消息异常", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = extractUserId(session);
        if (userId != null) {
            userSessions.remove(userId);
            log.info("本地客户端已断开: userId={}, status={}", userId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = extractUserId(session);
        log.warn("WebSocket 传输错误: userId={}, error={}", userId, exception.getMessage());
    }

    /**
     * 向指定用户的本地客户端发送工具执行请求，等待结果返回
     */
    public String executeTool(Long userId, String tool, Map<String, Object> args) throws Exception {
        WebSocketSession session = userSessions.get(userId);
        if (session == null || !session.isOpen()) {
            throw new IllegalStateException("本地客户端未连接，请确保已启动本地 Agent 客户端并登录");
        }

        String requestId = "req-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        Map<String, Object> payload = Map.of(
                "requestId", requestId,
                "tool", tool,
                "args", args != null ? args : Map.of()
        );
        session.sendMessage(new TextMessage(om.writeValueAsString(payload)));

        // 等待结果，超时 30 秒
        String result;
        try {
            result = future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            throw new RuntimeException("工具执行超时或中断: " + e.getMessage(), e);
        }
        return result;
    }

    public boolean isClientConnected(Long userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }

    private Long extractUserId(WebSocketSession session) {
        // 从 query 参数提取 token
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;
        String query = uri.getQuery();
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                String token = param.substring(6);
                try {
                    Object loginId = StpUtil.getLoginIdByToken(token);
                    if (loginId != null) {
                        return Long.parseLong(loginId.toString());
                    }
                } catch (Exception e) {
                    log.warn("WebSocket token 验证失败: {}", token);
                    return null;
                }
            }
        }
        return null;
    }
}
