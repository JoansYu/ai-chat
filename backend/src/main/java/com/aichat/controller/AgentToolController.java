package com.aichat.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.aichat.dto.Result;
import com.aichat.websocket.ToolExecutionProxy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/agent/tools")
@SaCheckLogin
public class AgentToolController {
    private static final Set<String> TOOLS = Set.of("get_project_context", "read_file", "read_file_region", "list_directory", "read_project_structure", "glob_files", "search_code", "get_symbol_info", "find_usages", "write_file", "edit_file", "create_directory", "get_file_info", "rename_symbol");
    private final ToolExecutionProxy proxy;
    public AgentToolController(ToolExecutionProxy proxy) { this.proxy = proxy; }
    public record Request(@NotBlank String tool, Map<String, Object> args, String clientId) {}
    @PostMapping("/execute")
    public Result<Map<String, String>> execute(@Valid @RequestBody Request request) {
        if (!TOOLS.contains(request.tool())) return Result.error("未知工具: " + request.tool());
        return Result.success(Map.of("result", proxy.execute(StpUtil.getLoginIdAsLong(), request.clientId(), request.tool(), request.args() == null ? Map.of() : request.args())));
    }
}
