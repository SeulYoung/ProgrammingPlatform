package com.example.server.controller;

import com.example.server.entity.User;
import com.example.server.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping(value = "/users")
public class TestController {
    private final UserMapper userMapper;

    @Autowired
    public TestController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public List<User> getUserList() {
        // 处理"/users/"的GET请求，用来获取用户列表
        // 还可以通过@RequestParam从页面中传递参数来进行查询条件或者翻页信息的传递
        return userMapper.getAll();
    }

    @RequestMapping(value = "/", method = RequestMethod.POST)
    public String postUser(@ModelAttribute User user) {
        // 处理"/users/"的POST请求，用来创建User
        // 除了@ModelAttribute绑定参数之外，还可以通过@RequestParam从页面中传递参数
        userMapper.insert(user);
        return "success";
    }

    @RequestMapping(value = "/{username}", method = RequestMethod.GET)
    public User getUser(@PathVariable String username) {
        // 处理"/users/{id}"的GET请求，用来获取url中id值的User信息
        // url中的id可通过@PathVariable绑定到函数的参数中
        return userMapper.getByName(username);
    }

    @RequestMapping(value = "/", method = RequestMethod.PUT)
    public String putUser(@ModelAttribute User user) {
        // 处理"/users/{id}"的PUT请求，用来更新User信息
        userMapper.update(user);
        return "success";
    }

    @RequestMapping(value = "/{username}", method = RequestMethod.DELETE)
    public String deleteUser(@PathVariable String username) {
        // 处理"/users/{id}"的DELETE请求，用来删除User
        userMapper.delete(username);
        return "success";
    }
}
