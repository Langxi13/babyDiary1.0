package com.langxi.babydiary.service;

import com.langxi.babydiary.details.CustomUserDetails;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.UserMapper;
import com.langxi.babydiary.mapper.AccountSecurityMapper;
import com.langxi.babydiary.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.List;

@Service
public class LoginService implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    private PasswordEncoder passwordEncoder;

    @Autowired
    private InvitationCodeService invitationCodeService;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private AccountSecurityMapper accountSecurityMapper;

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
        String role = user.getSystemRole() == null ? "USER" : user.getSystemRole();
        List<SimpleGrantedAuthority> authorities = "ADMIN".equals(role)
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new CustomUserDetails(
                user.getUserId(),
                user.getUsername(),
                user.getPassword(),
                user.getTokenVersion(),
                authorities
        );
    }

    @Transactional
    public void registerUser(String username, String password, String invitationCode) {
        String normalizedUsername = username == null ? "" : username.trim();
        User existingUser = userMapper.findByUsername(normalizedUsername);
        if (existingUser != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        if (!invitationCodeService.matches(invitationCode)) {
            throw new BusinessException(ErrorCode.INVALID_INVITATION_CODE);
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.insertUser(user);
        if (userMapper.countUsers() == 1) {
            userMapper.updateSystemRole(user.getUserId(), "ADMIN");
        }
        spaceService.ensurePersonalSpace(user.getUserId(), normalizedUsername);
    }

    public User findByUsername(String username) {
        return enrichAvatar(userMapper.findByUsername(username));
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
        accountSecurityMapper.revokeAllSessions(userId);
        accountSecurityMapper.deleteAccountTokens(userId, "STEP_UP");
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
            String spaceId = spaceService.requirePersonalSpace(userId).getPublicId();
            String assetPublicId = mediaService.upload(spaceId, userId, avatarFile, null,
                    "用户头像", null, null, null, null, null).getAssetId();
            var asset = mediaService.requireOwnedAsset(spaceId, assetPublicId, userId);
            mediaService.updateUsage(spaceId, assetPublicId, userId, "PROFILE", false);
            userMapper.updateAvatarAsset(userId, asset.getAssetId());
            return enrichAvatar(userMapper.findById(userId));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "头像上传失败");
        }
    }

    private User enrichAvatar(User user) {
        if (user != null && user.getAvatarAssetId() != null) {
            var asset = mediaService.findByPublicId(user.getAvatarAssetId());
            if (asset != null) user.setAvatarMedia(mediaService.toVO(asset));
        }
        return user;
    }
}
