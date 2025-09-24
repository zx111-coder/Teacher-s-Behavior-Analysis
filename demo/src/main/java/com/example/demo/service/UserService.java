package com.example.demo.service;

import com.example.demo.entity.Users;
import com.example.demo.entity.Video;
import com.example.demo.mapper.UserMapper;

import com.example.demo.mapper.VideoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private VideoMapper videoMapper;

    public Users login(Users user){
        Users loginUser = userMapper.getByNameAndPassword(user);
        return loginUser;
    }

    public Users register(Users user){
        int registerResult = userMapper.addUser(user);
        if(registerResult == 1){
            return login(user);
        }
        else{
            return null;
        }
    }

    public List<Video> getVideosByUserId(Long userId) {
        return videoMapper.findByUserId(userId);
    }

    public List<Users> getUser() {
        return userMapper.findAllUser();
    }

    public int deleteUserById(int userId) {
        return userMapper.deleteByUserId(userId);
    }

    public int change(Users user){
        return userMapper.updateUser(user);
    }
}
