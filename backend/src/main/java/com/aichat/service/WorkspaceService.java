package com.aichat.service;

import cn.dev33.satoken.stp.StpUtil;
import com.aichat.entity.UserWorkspaceEntity;
import com.aichat.mapper.UserWorkspaceMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;

@Service
public class WorkspaceService {

    private final UserWorkspaceMapper mapper;

    public WorkspaceService(UserWorkspaceMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 获取当前登录用户的已授权工作区路径
     */
    public String getAuthorizedWorkspacePath() {
        Long userId = StpUtil.getLoginIdAsLong();
        UserWorkspaceEntity entity = mapper.selectAuthorizedByUserId(userId);
        return entity != null ? entity.getWorkspacePath() : null;
    }

    /**
     * 获取当前登录用户的工作区授权记录
     */
    public UserWorkspaceEntity getAuthorizedWorkspace() {
        Long userId = StpUtil.getLoginIdAsLong();
        return mapper.selectAuthorizedByUserId(userId);
    }

    /**
     * 授权工作区路径
     */
    public UserWorkspaceEntity authorize(String workspacePath) {
        Long userId = StpUtil.getLoginIdAsLong();

        // 校验路径真实存在且是目录
        File dir = new File(workspacePath);
        if (!dir.exists()) {
            throw new IllegalArgumentException("路径不存在：" + workspacePath);
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("路径不是目录：" + workspacePath);
        }

        // 规范化为绝对路径
        String absPath = dir.getAbsolutePath();

        UserWorkspaceEntity entity = new UserWorkspaceEntity();
        entity.setUserId(userId);
        entity.setWorkspacePath(absPath);
        entity.setAuthorized(1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.insert(entity);

        return entity;
    }

    /**
     * 撤销工作区授权
     */
    public void revoke() {
        Long userId = StpUtil.getLoginIdAsLong();
        UserWorkspaceEntity entity = mapper.selectAuthorizedByUserId(userId);
        if (entity != null) {
            entity.setAuthorized(0);
            entity.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(entity);
        }
    }
}
