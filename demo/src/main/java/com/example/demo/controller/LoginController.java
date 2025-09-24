package com.example.demo.controller;

import com.example.demo.entity.Result;
import com.example.demo.entity.Users;
import com.example.demo.service.UserService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;

@Slf4j
@RestController
public class LoginController {
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result login(@RequestBody @Valid Users user) {
        // 验证必要参数
        if (user.getUsername() == null || user.getPassword() == null) {
            log.warn("登录参数缺失: {}", user);
            return Result.error("用户名和密码不能为空");
        }

        log.info("用户登录请求: {}", user.getUsername());

        Users t = userService.login(user);

        if (t == null) {
            log.warn("登录失败: {}", user.getUsername());
            return Result.error("用户名或密码错误");
        }
        log.info("登录成功: {}", user.getUsername());

        return Result.success(t); // 建议返回用户信息
    }
}