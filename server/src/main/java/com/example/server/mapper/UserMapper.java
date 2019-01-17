package com.example.server.mapper;

import com.example.server.entity.User;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface UserMapper {
    @Select("SELECT * FROM users")
    @Results({
            @Result(property = "username", column = "username"),
            @Result(property = "password", column = "password")
    })
    List<User> getAll();

    @Select("SELECT * FROM users WHERE username = #{username}")
    @Results({
            @Result(property = "username", column = "username"),
            @Result(property = "password", column = "password")
    })
    User getByName(String username);

    @Insert("INSERT INTO users(username,password) VALUES(#{username}, #{password})")
    void insert(User user);

    @Update("UPDATE users SET password = #{password} WHERE username = #{username}")
    void update(User user);

    @Delete("DELETE FROM users WHERE username = #{username}")
    void delete(String username);
}
