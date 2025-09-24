package com.example.demo.controller;

import com.example.demo.Exception.ServiceException;
import com.example.demo.entity.Result;
import com.example.demo.entity.Users;
import com.example.demo.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class RegisterController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result register(@RequestBody @Valid Users user) {
        // 验证必要参数
        if (user.getUsername() == null || user.getPassword() == null) {
            log.warn("注册参数缺失: {}", user);
            return Result.error("用户名和密码不能为空");
        }

        //先进行判断，这个用户名有没有重复
        if (userService.login(user) != null){
            throw new ServiceException("用户名已存在！");//用户已存在
        }

        Users registerUser = userService.register(user);
        return Result.success(registerUser);
    }
}
