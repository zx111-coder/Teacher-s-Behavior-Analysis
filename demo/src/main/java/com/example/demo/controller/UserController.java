package com.example.demo.controller;

import com.example.demo.entity.Users;
import com.example.demo.entity.Video;
import com.example.demo.service.UserService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/user/{userId}") //历史记录
    public List<Video> getVideosByUserId(@PathVariable Long userId) {
        return userService.getVideosByUserId(userId);
    }

    @GetMapping("/admin") //查看所有用户
    public List<Users> getUsers() {
        return userService.getUser();
    }

     @PostMapping("/change") //修改用户
     public int change(@RequestBody @Valid Users user, @RequestParam("actionType") String actionType) {
         if(actionType.equals("1")){ //删
             return userService.deleteUserById(user.getId());
         }
         if(actionType.equals("2")){ //改
             return userService.change(user);
         }
         return 0;
     }
}
