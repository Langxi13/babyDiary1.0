package com.langxi.babydiary.service;

import com.langxi.babydiary.details.CustomUserDetails;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.UserMapper;
import com.langxi.babydiary.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;

@Service
public class LoginService implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    private PasswordEncoder passwordEncoder;

    @Value("${invitationCode}")
    private String privateInvitationCode;

    @Autowired
    private ImageStorageService imageStorageService;

    @Autowired
    public void setPasswordEncoder(@Lazy PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return new CustomUserDetails(
                user.getUserId(),
                user.getUsername(),
                user.getPassword(),
                user.getTokenVersion(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    public void registerUser(String username, String password, String invitationCode) {
        String normalizedUsername = username == null ? "" : username.trim();
        User existingUser = userMapper.findByUsername(normalizedUsername);
        if (existingUser != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        if (!isValidInvitationCode(invitationCode)) {
            throw new BusinessException(ErrorCode.INVALID_INVITATION_CODE);
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.insertUser(user);
    }

    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    private boolean isValidInvitationCode(String invitationCode) {
        return privateInvitationCode.equals(invitationCode);
    }

    @Transactional
    public void changePassword(Integer userId, String oldPassword, String newPassword, String confirmPassword) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        userMapper.updatePasswordAndIncrementTokenVersion(userId, passwordEncoder.encode(newPassword));
    }

    @Transactional
    public User updateAvatar(Integer userId, MultipartFile avatarFile) {
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "头像文件不能为空");
        }
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        try {
            String prefix = avatarPrefix(userId);
            String fileName = imageStorageService.storeImage(avatarFile, prefix, false);
            userMapper.updateAvatarPath(userId, fileName);
            if (imageStorageService.isOwnedPath(user.getAvatarPath(), prefix)) {
                imageStorageService.deleteAfterCommit(user.getAvatarPath());
            }
            return userMapper.findById(userId);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "头像上传失败");
        }
    }

    private String avatarPrefix(Integer userId) {
        return "avatar_" + userId + "_";
    }
}
