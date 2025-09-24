package com.example.demo.mapper;

import java.util.List;

import org.apache.ibatis.annotations.*;
import com.example.demo.entity.Users;

@Mapper
public interface UserMapper {
    @Select("select * from Users")
    public List<Users> findAllUser();

    //登录
    @Select("select id, username, role from Users where username=#{username} and password=#{password}")
    public Users getByNameAndPassword(Users user);

    //注册
    @Insert("INSERT INTO Users(username, password, role) VALUES(#{username}, #{password}, 'user')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    public int addUser(Users user);

    @Delete("DELETE FROM users WHERE id = #{id}")
    public int deleteByUserId(int id);

    public int updateUser(Users user);
}


