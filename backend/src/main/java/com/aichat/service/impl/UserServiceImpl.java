package com.aichat.service.impl;

import cn.dev33.satoken.secure.SaSecureUtil;
import com.aichat.entity.UserEntity;
import com.aichat.exception.BusinessException;
import com.aichat.mapper.UserMapper;
import com.aichat.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private static final String PASSWORD_SALT = "AI_Chat_System_2026_!@#";

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void register(String username, String password) {
        if (userMapper.selectByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }
        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        entity.setPassword(SaSecureUtil.sha256BySalt(password, PASSWORD_SALT));
        userMapper.insert(entity);
    }

    @Override
    public Long login(String username, String password) {
        UserEntity entity = userMapper.selectByUsername(username);
        if (entity == null) {
            throw new BusinessException("用户名或密码不正确");
        }
        String encryptPassword = SaSecureUtil.sha256BySalt(password, PASSWORD_SALT);
        if (!encryptPassword.equals(entity.getPassword())) {
            throw new BusinessException("用户名或密码不正确");
        }
        return entity.getId();
    }
}
