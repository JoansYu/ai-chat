package com.aichat.websocket;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolExecutionProxyTest {
    @Test void disconnectedIdeNeverFallsBackToAnotherProject() throws Exception {
        var handler = mock(AgentToolWebSocketHandler.class);
        when(handler.resolveWorkspace(7L, "project-a")).thenReturn(
                new AgentToolWebSocketHandler.ProjectWorkspace("project-a", "F:/project-a"));
        when(handler.executeTool(7L, "project-a", "write_file", Map.of())).thenThrow(new IllegalStateException("disconnected"));
        String result = new ToolExecutionProxy(handler).execute(7L, "project-a", "write_file", Map.of());
        assertTrue(result.contains("disconnected"));
        verify(handler).resolveWorkspace(7L, "project-a");
        verify(handler).executeTool(7L, "project-a", "write_file", Map.of());
        verifyNoMoreInteractions(handler);
    }

    @Test void missingClientIdUsesAutomaticallyResolvedProject() throws Exception {
        var handler = mock(AgentToolWebSocketHandler.class);
        when(handler.resolveWorkspace(7L, null)).thenReturn(
                new AgentToolWebSocketHandler.ProjectWorkspace("project-a", "F:/project-a"));
        when(handler.executeTool(7L, "project-a", "read_file", Map.of())).thenReturn("content");
        assertEquals("content", new ToolExecutionProxy(handler).execute(7L, "read_file", Map.of()));
    }

    @Test void missingConnectionStopsBeforeToolExecution() {
        var handler = mock(AgentToolWebSocketHandler.class);
        when(handler.resolveWorkspace(7L, null)).thenThrow(new IllegalStateException("请先连接插件"));
        assertTrue(new ToolExecutionProxy(handler).execute(7L, "read_file", Map.of()).contains("请先连接插件"));
        verify(handler).resolveWorkspace(7L, null);
        verifyNoMoreInteractions(handler);
    }
}
