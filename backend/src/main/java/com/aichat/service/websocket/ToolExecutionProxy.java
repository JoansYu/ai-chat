package com.aichat.websocket;

import org.springframework.stereotype.Component;
import java.util.Map;

/** All project tools execute in the connected IDEA project. */
@Component
public class ToolExecutionProxy {
    private final AgentToolWebSocketHandler wsHandler;

    public ToolExecutionProxy(AgentToolWebSocketHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    public String execute(Long userId, String clientId, String tool, Map<String, Object> args) {
        try {
            String target = wsHandler.resolveWorkspace(userId, clientId).clientId();
            return wsHandler.executeTool(userId, target, tool, args);
        } catch (Exception e) {
            return "工具执行失败（当前 IDEA 项目）: " + e.getMessage();
        }
    }

    public String execute(Long userId, String tool, Map<String, Object> args) {
        return execute(userId, null, tool, args);
    }
}
