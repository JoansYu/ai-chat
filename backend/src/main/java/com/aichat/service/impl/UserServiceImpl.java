package com.aichat.service.impl;

import cn.dev33.satoken.secure.SaSecureUtil;
import com.aichat.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private static final String PASSWORD_SALT = "AI_Chat_System_2026_!@#";

    @Override
    public void register(String username, String password) {
        String encryptPassword = SaSecureUtil.sha256BySalt(password, PASSWORD_SALT);
        System.out.println("注册成功！用户：" + username + "，加密后的密码存入DB：" + encryptPassword);
    }

    @Override
    public Long login(String username, String password) {
        Long mockDbUserId = 10001L;
        String encryptPassword = SaSecureUtil.sha256BySalt(password, PASSWORD_SALT);

        String mockDbPassword = SaSecureUtil.sha256BySalt(password, PASSWORD_SALT); // 假装这是从DB查出来的密文

        // if (!encryptPassword.equals(dbUser.getPassword())) {
        if (!encryptPassword.equals(mockDbPassword)) {
            throw new RuntimeException("密码错误");
        }

        // 3. 比对成功，返回用户的唯一标识 ID
        // return dbUser.getId();
        return mockDbUserId;
    }
}
