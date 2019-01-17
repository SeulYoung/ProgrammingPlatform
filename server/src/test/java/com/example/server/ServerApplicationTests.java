package com.example.server;

import com.example.server.entity.User;
import com.example.server.mapper.UserMapper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ServerApplicationTests {

    @Autowired
    private UserMapper userMapper;

    @Test
    @Rollback
    public void testUserMapper() {
        User u = new User();
        u.setUsername("ddd");
        u.setPassword("12345");
        userMapper.insert(u);
        u = userMapper.getByName("ddd");
        Assert.assertEquals("12345", u.getPassword());

        u.setPassword("1234567");
        userMapper.update(u);
        u = userMapper.getByName("ddd");
        Assert.assertEquals("1234567", u.getPassword());

        userMapper.delete(u.getUsername());
        u = userMapper.getByName("ddd");
        Assert.assertNull(u);
    }

}

