package com.aichat.mapper;

import com.aichat.entity.ChatSessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {

    @Select("SELECT * FROM t_chat_session ORDER BY created_at DESC")
    List<ChatSessionEntity> selectAllOrderByCreatedDesc();
}
