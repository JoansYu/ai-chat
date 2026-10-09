package com.aichat.websocket;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentToolWebSocketHandlerTest {
    private WebSocketSession session(String id, String client) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/agent-tools?token=test&clientId=" + client + "&workspace=F%3A%2F" + client));
        return session;
    }
    @Test void routesToProjectAndRejectsRepliesFromAnotherConnection() throws Exception {
        AgentToolWebSocketHandler handler = new AgentToolWebSocketHandler();
        WebSocketSession a = session("a", "project-a"), b = session("b", "project-b");
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.getLoginIdByToken("test")).thenReturn("7");
            handler.afterConnectionEstablished(a);
            handler.afterConnectionEstablished(b);
        }
        assertEquals("F:/project-a", handler.workspace(7L, "project-a"));
        assertThrows(IllegalStateException.class, () -> handler.workspace(8L, "project-a"));
        BlockingQueue<String> sent = new LinkedBlockingQueue<>();
        doAnswer(invocation -> { sent.add(((TextMessage) invocation.getArgument(0)).getPayload()); return null; }).when(a).sendMessage(any());
        CompletableFuture<String> result = CompletableFuture.supplyAsync(() -> {
            try { return handler.executeTool(7L, "project-a", "read_file", Map.of("path", "A.java")); }
            catch (Exception e) { throw new CompletionException(e); }
        });
        String payload = sent.poll(3, TimeUnit.SECONDS);
        assertNotNull(payload);
        ObjectMapper mapper = new ObjectMapper();
        String requestId = mapper.readTree(payload).path("requestId").asText();
        TextMessage reply = new TextMessage(mapper.writeValueAsString(Map.of("requestId", requestId, "result", "content", "error", false)));
        handler.handleTextMessage(b, reply);
        assertFalse(result.isDone());
        handler.handleTextMessage(a, reply);
        assertEquals("content", result.get(3, TimeUnit.SECONDS));
        verify(b, never()).sendMessage(any());
    }
    @Test void staleCloseDoesNotRemoveReplacement() throws Exception {
        AgentToolWebSocketHandler handler = new AgentToolWebSocketHandler();
        WebSocketSession old = session("old", "same"), replacement = session("new", "same");
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.getLoginIdByToken("test")).thenReturn("7");
            handler.afterConnectionEstablished(old);
            handler.afterConnectionEstablished(replacement);
        }
        handler.afterConnectionClosed(old, CloseStatus.NORMAL);
        assertEquals("F:/same", handler.workspace(7L, "same"));
        handler.afterConnectionClosed(replacement, CloseStatus.NORMAL);
        assertThrows(IllegalStateException.class, () -> handler.workspace(7L, "same"));
    }

    @Test void autoSelectsOnlyCurrentUsersLiveProject() throws Exception {
        var handler = new AgentToolWebSocketHandler();
        var a = session("a", "project-a");
        var b = session("b", "project-b");
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.getLoginIdByToken("test")).thenReturn("7");
            handler.afterConnectionEstablished(a);
            auth.when(() -> StpUtil.getLoginIdByToken("test")).thenReturn("8");
            handler.afterConnectionEstablished(b);
        }
        assertEquals(new AgentToolWebSocketHandler.ProjectWorkspace("project-a", "F:/project-a"),
                handler.resolveWorkspace(7L, null));
        assertEquals("project-a", handler.resolveWorkspace(7L, " ").clientId());
        assertEquals(1, handler.workspaces(7L).size());
        assertThrows(IllegalStateException.class, () -> handler.resolveWorkspace(7L, "project-b"));
        when(a.isOpen()).thenReturn(false);
        assertTrue(handler.workspaces(7L).isEmpty());
        assertThrows(IllegalStateException.class, () -> handler.resolveWorkspace(7L, null));
        assertThrows(IllegalStateException.class, () -> handler.resolveWorkspace(7L, "project-a"));
    }

    @Test void multipleProjectsRequireExplicitTargetAndStaleIdDoesNotSwitchProject() throws Exception {
        var handler = new AgentToolWebSocketHandler();
        var a = session("a", "project-a");
        var b = session("b", "project-b");
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.getLoginIdByToken("test")).thenReturn("7");
            handler.afterConnectionEstablished(a);
            handler.afterConnectionEstablished(b);
        }
        assertThrows(IllegalStateException.class, () -> handler.resolveWorkspace(7L, null));
        assertEquals("F:/project-b", handler.resolveWorkspace(7L, "project-b").workspacePath());
        handler.afterConnectionClosed(a, CloseStatus.NORMAL);
        assertThrows(IllegalStateException.class, () -> handler.resolveWorkspace(7L, "project-a"));
        assertEquals("project-b", handler.resolveWorkspace(7L, null).clientId());
    }

    @Test void rejectsMissingProjectIdentityOrWorkspace() throws Exception {
        var handler = new AgentToolWebSocketHandler();
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.getLoginIdByToken("test")).thenReturn("7");
            for (String query : new String[]{"", "&workspace=F%3A%2Fproject", "&clientId=project"}) {
                var connection = session("invalid", "invalid");
                when(connection.getUri()).thenReturn(URI.create("ws://localhost/ws/agent-tools?token=test" + query));
                handler.afterConnectionEstablished(connection);
                verify(connection).close(CloseStatus.NOT_ACCEPTABLE);
            }
        }
        assertTrue(handler.workspaces(7L).isEmpty());
    }

    @Test void preservesRemoteProjectPathWithUnicodeAndSpaces() throws Exception {
        var handler = new AgentToolWebSocketHandler();
        var connection = session("remote", "remote-project");
        String path = "/home/developer/项目 folder";
        when(connection.getUri()).thenReturn(URI.create("ws://localhost/ws/agent-tools?token=test&clientId=remote-project&workspace="
                + java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8)));
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.getLoginIdByToken("test")).thenReturn("7");
            handler.afterConnectionEstablished(connection);
        }
        assertEquals(path, handler.resolveWorkspace(7L, null).workspacePath());
    }
}
