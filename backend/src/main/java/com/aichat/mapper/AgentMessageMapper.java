package com.aichat.mapper;

import com.aichat.entity.AgentMessageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AgentMessageMapper extends BaseMapper<AgentMessageEntity> {

    @Select("SELECT * FROM t_agent_message WHERE user_id = #{userId} AND session_id = #{sessionId} ORDER BY seq ASC")
    List<AgentMessageEntity> selectByUserAndSession(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Delete("DELETE FROM t_agent_message WHERE user_id = #{userId} AND session_id = #{sessionId}")
    int deleteByUserAndSession(@Param("userId") Long userId, @Param("sessionId") String sessionId);
}
