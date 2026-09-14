package com.aichat.mapper;

import com.aichat.entity.UserWorkspaceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserWorkspaceMapper extends BaseMapper<UserWorkspaceEntity> {

    @Select("SELECT * FROM t_user_workspace WHERE user_id = #{userId} AND authorized = 1 ORDER BY updated_at DESC LIMIT 1")
    UserWorkspaceEntity selectAuthorizedByUserId(@Param("userId") Long userId);
}
