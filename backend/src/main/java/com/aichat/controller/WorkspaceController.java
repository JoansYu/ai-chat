package com.aichat.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.aichat.dto.Result;
import com.aichat.websocket.AgentToolWebSocketHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workspace")
@SaCheckLogin
public class WorkspaceController {
    private final AgentToolWebSocketHandler projectClients;

    public WorkspaceController(AgentToolWebSocketHandler projectClients) {
        this.projectClients = projectClients;
    }

    public record Status(boolean connected, String workspacePath,
                         List<AgentToolWebSocketHandler.ProjectWorkspace> projects) {}

    @GetMapping
    public Result<Status> status() {
        var projects = projectClients.workspaces(StpUtil.getLoginIdAsLong());
        return Result.success(new Status(!projects.isEmpty(),
                projects.size() == 1 ? projects.getFirst().workspacePath() : "", projects));
    }
}
