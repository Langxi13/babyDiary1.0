package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    User findById(@Param("userId") Integer userId);

    User findByUsername(@Param("username") String username);

    //新增用户
    void insertUser(User user);

    //通过用户id查找avatarPath
    //更新avatarPath
    void updateAvatarPath(@Param("userId") Integer userId, @Param("avatarPath") String avatarPath);

    void updatePasswordAndIncrementTokenVersion(@Param("userId") Integer userId, @Param("password") String password);
}
