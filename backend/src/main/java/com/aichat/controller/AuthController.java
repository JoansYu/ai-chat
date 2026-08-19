package com.aichat.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aichat.dto.AuthRequest;
import com.aichat.dto.Result;
import com.aichat.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    @Resource
    private UserService userService;

    @PostMapping("/login")
    public Result<Object> login(@RequestBody AuthRequest request) {
        Long userId = userService.login(request.getUsername(), request.getPassword());
        StpUtil.login(userId);
        return Result.success(StpUtil.getTokenInfo());
    }

    @PostMapping("/register")
    public Result<Object> register(@RequestBody AuthRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            return Result.error("用户名或密码不能为空");
        }
        userService.register(request.getUsername(), request.getPassword());
        return Result.success("注册成功");
    }

    @PostMapping("/logout")
    public Result<Object> logout() {
        StpUtil.logout();
        return Result.success("已登出");
    }
}
