package com.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_chat_session")
public class ChatSessionEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private Long userId;

    private String title;

    private Long createdAt;
}
