package com.aichat.mapper;

import com.aichat.entity.UserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT * FROM t_user WHERE username = #{username}")
    UserEntity selectByUsername(@Param("username") String username);
}
