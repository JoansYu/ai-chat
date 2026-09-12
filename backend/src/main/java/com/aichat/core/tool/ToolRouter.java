package com.aichat.core.tool;

import com.aichat.core.service.Tool;
import com.aichat.model.AgentContext;
import com.aichat.model.ToolCall;
import com.aichat.model.ToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册与分发
 */
@Component
public class ToolRouter {

    private final Map<String, Tool> registry = new ConcurrentHashMap<>();

    public ToolRouter(List<Tool> tools) {
        if (CollectionUtils.isEmpty(tools)) {
            return;
        }
        for (Tool tool : tools) {
            register(tool);
        }

        // 加一行启动日志，用来确认是否真的注册成功了
        System.out.println("✅ [ToolRouter] 成功自动注入并注册了 " + registry.size() + " 个大模型工具！");
    }

    public void register(Tool t) {
        registry.put(t.name(), t);
    }

    public ToolResult dispatch(ToolCall tc, AgentContext ctx) {
        Tool t = registry.get(tc.name());
        if (t == null) {
            return ToolResult.fail("未知工具：" + tc.name());
        }
        return t.run(tc.arguments(), ctx);
    }

    public List<Map<String, Object>> schemas() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tool t:registry.values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("type", "function");
            Map<String, Object> fn = new HashMap<>();
            fn.put("name", t.name());
            fn.put("description", t.description());
            fn.put("parameters", t.parameters());
            m.put("function", fn);
            list.add(m);
        }
        return list;
    }
}
