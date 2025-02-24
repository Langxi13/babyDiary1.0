package com.langxi.babydiary.service;

import com.langxi.babydiary.details.CustomUserDetails;
import com.langxi.babydiary.mapper.UserMapper;
import com.langxi.babydiary.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class LoginService implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("Loading user by username: " + username);
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        System.out.println("User found: " + user.getUsername() + ", Password: " + user.getPassword());
        return new CustomUserDetails(
                user.getUserId(),
                user.getUsername(),
                user.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    // 注册用户
    public void registerUser(String username, String password, String invitationCode) {
        // 检查用户名是否已存在
        User existingUser = userMapper.findByUsername(username);
        if (existingUser != null) {
            throw new RuntimeException("Username already exists");
        }
        // 检查邀请码是否有效
        if (!isValidInvitationCode(invitationCode)) {
            throw new RuntimeException("Invalid invitation code");
        }

        // 创建新用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password)); // 加密密码
        userMapper.insertUser(user);
    }

    // 根据用户名查询用户
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    // 校验邀请码是否有效
    private boolean isValidInvitationCode(String invitationCode) {
        // 这里可以自定义邀请码的校验逻辑
        // 例如：邀请码必须为 "BABY2023"
        return "LangxiundAngel".equals(invitationCode);
    }
}