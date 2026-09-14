package com.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_workspace")
public class UserWorkspaceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String workspacePath;

    private Integer authorized;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
