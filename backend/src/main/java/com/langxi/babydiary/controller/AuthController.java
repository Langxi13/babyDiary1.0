package com.langxi.babydiary.controller;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.details.CustomUserDetails;
import com.langxi.babydiary.dto.LoginDTO;
import com.langxi.babydiary.dto.LoginVO;
import com.langxi.babydiary.dto.PasswordChangeDTO;
import com.langxi.babydiary.dto.RegisterDTO;
import com.langxi.babydiary.dto.UserVO;
import com.langxi.babydiary.entity.User;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.security.JwtTokenProvider;
import com.langxi.babydiary.service.LoginService;
import com.langxi.babydiary.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理", description = "用户登录、注册、登出等认证相关接口")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private LoginService loginService;

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private RateLimitService rateLimitService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "通过用户名和密码登录，返回JWT Token")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        rateLimitService.require("legacy-login",
                rateLimitService.clientAddress(request) + ":" + loginDTO.getUsername(), 10, Duration.ofMinutes(15));
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtTokenProvider.generateToken(authentication);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        User user = loginService.findByUsername(userDetails.getUsername());
        UserVO userVO = UserVO.fromEntity(user);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setExpiresIn(jwtTokenProvider.getExpiration());
        loginVO.setUserInfo(userVO);

        log.info("用户登录成功: {}", user.getUsername());
        return Result.success("登录成功", loginVO);
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "通过用户名、密码和邀请码注册新用户")
    public Result<Void> register(@Valid @RequestBody RegisterDTO registerDTO) {
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        loginService.registerUser(registerDTO.getUsername(), registerDTO.getPassword(), registerDTO.getInvitationCode());
        log.info("用户注册成功: {}", registerDTO.getUsername());
        return Result.success("注册成功", null);
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户登出，清除认证信息")
    public Result<Void> logout() {
        SecurityContextHolder.clearContext();
        return Result.success("登出成功", null);
    }

    @GetMapping("/info")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    public Result<UserVO> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = loginService.findByUsername(userDetails.getUsername());

        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return Result.success(UserVO.fromEntity(user));
    }

    @PostMapping("/avatar")
    @Operation(summary = "更新头像", description = "上传当前用户头像")
    public Result<UserVO> updateAvatar(@Parameter(description = "头像文件") @RequestParam("avatarFile") MultipartFile avatarFile) {
        User user = loginService.updateAvatar(currentUser.getUserId(), avatarFile);
        return Result.success("头像更新成功", UserVO.fromEntity(user));
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "修改当前用户密码，成功后旧Token立即失效")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeDTO passwordChangeDTO) {
        loginService.changePassword(
                currentUser.getUserId(),
                passwordChangeDTO.getOldPassword(),
                passwordChangeDTO.getNewPassword(),
                passwordChangeDTO.getConfirmPassword()
        );
        SecurityContextHolder.clearContext();
        return Result.success("密码修改成功，请重新登录", null);
    }
}
