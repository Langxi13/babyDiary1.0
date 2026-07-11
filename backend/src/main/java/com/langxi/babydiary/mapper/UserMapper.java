package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    User findById(@Param("userId") Integer userId);

    User findByUsername(@Param("username") String username);

    User findByEmail(@Param("email") String email);

    //新增用户
    void insertUser(User user);

    //通过用户id查找avatarPath
    //更新avatarPath
    void updateAvatarPath(@Param("userId") Integer userId, @Param("avatarPath") String avatarPath);

    void updatePasswordAndIncrementTokenVersion(@Param("userId") Integer userId, @Param("password") String password);

    void updateEmail(@Param("userId") Integer userId, @Param("email") String email, @Param("verified") boolean verified);

    void markEmailVerified(@Param("userId") Integer userId);

    void updateTimezone(@Param("userId") Integer userId, @Param("timezone") String timezone);

    int countUsers();

    void updateSystemRole(@Param("userId") Integer userId, @Param("systemRole") String systemRole);
}
