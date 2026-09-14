package com.aichat.mapper;

import com.aichat.entity.ChatMessageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {

    @Select("SELECT id, session_id, role, content, msg_timestamp AS timestamp FROM t_chat_message WHERE session_id = #{sessionId} ORDER BY msg_timestamp ASC")
    List<ChatMessageEntity> selectBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT id, session_id, role, content, msg_timestamp AS timestamp FROM t_chat_message WHERE session_id = #{sessionId} ORDER BY msg_timestamp DESC LIMIT #{n}")
    List<ChatMessageEntity> selectRecentBySessionId(@Param("sessionId") String sessionId, @Param("n") int n);

    @Select("SELECT COUNT(*) FROM t_chat_message WHERE session_id = #{sessionId}")
    int countBySessionId(@Param("sessionId") String sessionId);

    @Delete("DELETE FROM t_chat_message WHERE session_id = #{sessionId}")
    int deleteBySessionId(@Param("sessionId") String sessionId);
}
