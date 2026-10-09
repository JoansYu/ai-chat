package com.aichat.websocket;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.*;

/** Routes by authenticated user AND IDE project connection, never by the last connected window. */
@Component
public class AgentToolWebSocketHandler extends TextWebSocketHandler {
    public record ProjectWorkspace(String clientId, String workspacePath) {}
    private record Key(Long userId, String clientId) {}
    private record Client(WebSocketSession session, String workspace) {}
    private record Pending(WebSocketSession owner, CompletableFuture<String> future) {}
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Key, Client> clients = new ConcurrentHashMap<>();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            session.setTextMessageSizeLimit(2 * 1024 * 1024);
            Map<String, String> query = new java.util.HashMap<>();
            String raw = session.getUri() == null ? null : session.getUri().getRawQuery();
            if (raw != null) for (String part : raw.split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2) query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
            Object loginId = StpUtil.getLoginIdByToken(query.get("token"));
            if (loginId == null) throw new IllegalArgumentException("未登录");
            String clientId = query.getOrDefault("clientId", "");
            String workspace = query.getOrDefault("workspace", "");
            if (clientId.isBlank() || clientId.length() > 128 || workspace.isBlank()) throw new IllegalArgumentException("无效项目连接");
            Key key = new Key(Long.valueOf(loginId.toString()), clientId);
            session.getAttributes().put("agentKey", key);
            Client previous = clients.put(key, new Client(session, workspace));
            if (previous != null) previous.session().close(CloseStatus.NORMAL);
        } catch (Exception e) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = mapper.readTree(message.getPayload());
        String id = node.path("requestId").asText();
        Pending request = pending.get(id);
        if (request == null || request.owner() != session || !pending.remove(id, request)) return;
        request.future().complete((node.path("error").asBoolean() ? "工具执行错误: " : "") + node.path("result").asText());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object key = session.getAttributes().get("agentKey");
        if (key instanceof Key k) clients.computeIfPresent(k, (ignored, client) -> client.session() == session ? null : client);
        pending.forEach((id, request) -> {
            if (request.owner() == session && pending.remove(id, request)) request.future().completeExceptionally(new IllegalStateException("IDE 项目连接已断开"));
        });
    }

    public String workspace(Long userId, String clientId) {
        return requireClient(userId, clientId).workspace();
    }

    public List<ProjectWorkspace> workspaces(Long userId) {
        return clients.entrySet().stream()
                .filter(entry -> entry.getKey().userId().equals(userId) && entry.getValue().session().isOpen())
                .map(entry -> new ProjectWorkspace(entry.getKey().clientId(), entry.getValue().workspace()))
                .sorted(Comparator.comparing(ProjectWorkspace::workspacePath).thenComparing(ProjectWorkspace::clientId))
                .toList();
    }

    /** Resolve once per chat, then carry the same clientId through every tool callback. */
    public ProjectWorkspace resolveWorkspace(Long userId, String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            return new ProjectWorkspace(clientId, workspace(userId, clientId));
        }
        List<ProjectWorkspace> available = workspaces(userId);
        if (available.isEmpty()) throw new IllegalStateException("请先在 IDEA 项目中连接 AI Chat Agent 插件，工作区将自动使用该项目目录");
        if (available.size() > 1) throw new IllegalStateException("当前有多个 IDEA 项目连接，请在目标项目的插件窗口中发起对话");
        return available.getFirst();
    }

    private Client requireClient(Long userId, String clientId) {
        Client client = clients.get(new Key(userId, clientId == null ? "" : clientId));
        if (client == null || !client.session().isOpen()) throw new IllegalStateException("当前 IDEA 项目未连接，请在该项目窗口重新连接");
        return client;
    }

    public boolean isClientConnected(Long userId) {
        return !workspaces(userId).isEmpty();
    }

    public String executeTool(Long userId, String tool, Map<String, Object> args) throws Exception {
        return executeTool(userId, resolveWorkspace(userId, null).clientId(), tool, args);
    }

    public String executeTool(Long userId, String clientId, String tool, Map<String, Object> args) throws Exception {
        WebSocketSession session = requireClient(userId, clientId).session();
        String requestId = UUID.randomUUID().toString();
        Pending request = new Pending(session, new CompletableFuture<>());
        pending.put(requestId, request);
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("requestId", requestId, "tool", tool, "args", args == null ? Map.of() : args))));
            }
            return request.future().get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            pending.remove(requestId, request);
        }
    }
}
