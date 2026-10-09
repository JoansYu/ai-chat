package com.aichat.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aichat.core.service.AgentOrchestrator;
import com.aichat.guard.RateLimiter;
import com.aichat.model.AgentContext;
import com.aichat.model.ChatSession;
import com.aichat.service.SessionService;
import com.aichat.websocket.AgentToolWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentWorkspaceTest {
    @Test void webChatCarriesResolvedProjectIdentityIntoAgentContext() {
        var handler = mock(AgentToolWebSocketHandler.class);
        var orchestrator = mock(AgentOrchestrator.class);
        var limiter = mock(RateLimiter.class);
        var sessions = mock(SessionService.class);
        when(handler.resolveWorkspace(7L, null)).thenReturn(
                new AgentToolWebSocketHandler.ProjectWorkspace("ide-project", "/remote/项目"));
        when(limiter.tryAcquire("agent:7")).thenReturn(true);
        when(sessions.getOrCreate("chat")).thenReturn(new ChatSession("chat", "existing", 0, 1));
        when(orchestrator.streamChat(anyString(), any(), any())).thenReturn("answer");
        var controller = new AgentController(orchestrator, limiter, sessions, Runnable::run, handler);
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            auth.when(StpUtil::getTokenValue).thenReturn("token");
            controller.chat(new AgentController.AgentChatRequest("chat", "read project", null));
        }
        var context = ArgumentCaptor.forClass(AgentContext.class);
        verify(orchestrator).streamChat(eq("read project"), context.capture(), any());
        assertEquals("ide-project", context.getValue().clientId());
        assertEquals("/remote/项目", context.getValue().workspacePath());
        assertEquals("token", context.getValue().userToken());
    }

    @Test void unresolvedWorkspaceReturnsBeforeStartingAgent() {
        var handler = mock(AgentToolWebSocketHandler.class);
        var orchestrator = mock(AgentOrchestrator.class);
        var executor = mock(Executor.class);
        when(handler.resolveWorkspace(7L, "stale-project")).thenThrow(new IllegalStateException("项目已断线"));
        var controller = new AgentController(orchestrator, mock(RateLimiter.class), mock(SessionService.class), executor, handler);
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            auth.when(StpUtil::getTokenValue).thenReturn("token");
            assertNotNull(controller.chat(new AgentController.AgentChatRequest("chat", "read project", "stale-project")));
        }
        verifyNoInteractions(executor, orchestrator);
    }

    @Test void statusUsesLiveProjectsForAuthenticatedUser() {
        var handler = mock(AgentToolWebSocketHandler.class);
        var project = new AgentToolWebSocketHandler.ProjectWorkspace("ide-project", "/remote/project");
        when(handler.workspaces(7L)).thenReturn(List.of(project), List.of(), List.of(project, project));
        var controller = new WorkspaceController(handler);
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            var connected = controller.status().getData();
            assertTrue(connected.connected());
            assertEquals("/remote/project", connected.workspacePath());
            assertEquals(List.of(project), connected.projects());
            var disconnected = controller.status().getData();
            assertFalse(disconnected.connected());
            assertEquals("", disconnected.workspacePath());
            var multiple = controller.status().getData();
            assertTrue(multiple.connected());
            assertEquals("", multiple.workspacePath());
        }
    }
}
