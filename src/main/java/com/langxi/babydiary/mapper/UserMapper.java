package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    User findByUsername(@Param("username") String username);

    //新增用户
    void insertUser(User user);

    //通过用户名查找用户

}