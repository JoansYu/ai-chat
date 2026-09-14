package com.aichat.websocket;

import com.aichat.service.ToolExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工具执行代理：
 * 优先通过 WebSocket 转发给用户的本地客户端执行（方案 B）；
 * 如果本地客户端未连接，降级到服务器本地执行（开发/测试场景）。
 */
@Component
public class ToolExecutionProxy {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionProxy.class);

    private final AgentToolWebSocketHandler wsHandler;
    private final ToolExecutorService localExecutor;

    public ToolExecutionProxy(AgentToolWebSocketHandler wsHandler, ToolExecutorService localExecutor) {
        this.wsHandler = wsHandler;
        this.localExecutor = localExecutor;
    }

    public String execute(Long userId, String tool, Map<String, Object> args) {
        if (wsHandler.isClientConnected(userId)) {
            log.info("工具执行 → 转发到本地客户端: userId={}, tool={}", userId, tool);
            try {
                return wsHandler.executeTool(userId, tool, args);
            } catch (Exception e) {
                log.error("本地客户端工具执行失败，降级到服务器执行", e);
                return "工具执行失败（本地客户端）：" + e.getMessage();
            }
        } else {
            log.info("工具执行 → 服务器本地执行（无本地客户端连接）: tool={}", tool);
            return localExecutor.execute(tool, args);
        }
    }
}
