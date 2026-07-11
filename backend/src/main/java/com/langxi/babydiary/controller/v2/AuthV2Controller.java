package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.details.CustomUserDetails;
import com.langxi.babydiary.dto.*;
import com.langxi.babydiary.entity.User;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.AccountSecurityService;
import com.langxi.babydiary.service.AuthSessionService;
import com.langxi.babydiary.service.LoginService;
import com.langxi.babydiary.service.RateLimitService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v2/auth")
public class AuthV2Controller {
    private static final String REFRESH_COOKIE = "baby_diary_refresh";

    private final AuthenticationManager authenticationManager;
    private final LoginService loginService;
    private final AuthSessionService sessionService;
    private final AccountSecurityService securityService;
    private final CurrentUser currentUser;
    private final RateLimitService rateLimitService;

    @Value("${app.auth.refresh-days:30}")
    private int refreshDays;

    @Value("${app.auth.secure-cookie:false}")
    private boolean secureCookie;

    public AuthV2Controller(AuthenticationManager authenticationManager,
                            LoginService loginService,
                            AuthSessionService sessionService,
                            AccountSecurityService securityService,
                            CurrentUser currentUser,
                            RateLimitService rateLimitService) {
        this.authenticationManager = authenticationManager;
        this.loginService = loginService;
        this.sessionService = sessionService;
        this.securityService = securityService;
        this.currentUser = currentUser;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto,
                                 @RequestHeader(value = "X-Device-Name", required = false) String deviceName,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        rateLimitService.require("login", rateLimitService.clientAddress(request) + ":" + dto.getUsername(), 10, Duration.ofMinutes(15));
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        User user = loginService.findByUsername(principal.getUsername());
        AuthSessionService.SessionLogin login = sessionService.createSession(
                user, deviceName, request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
        writeRefreshCookie(response, login.refreshToken());
        return Result.success("登录成功", login.login());
    }

    @PostMapping("/refresh")
    public Result<LoginVO> refresh(HttpServletRequest request, HttpServletResponse response) {
        rateLimitService.require("refresh", rateLimitService.clientAddress(request), 120, Duration.ofHours(1));
        AuthSessionService.SessionLogin login = sessionService.refresh(readRefreshCookie(request));
        writeRefreshCookie(response, login.refreshToken());
        return Result.success(login.login());
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionService.logout(readRefreshCookie(request));
        clearRefreshCookie(response);
        return Result.success("已退出登录", null);
    }

    @GetMapping("/sessions")
    public Result<List<AuthSessionVO>> sessions(HttpServletRequest request) {
        return Result.success(sessionService.listSessions(currentUser.getUserId(), readRefreshCookie(request)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> revokeSession(@PathVariable String sessionId) {
        sessionService.revokeSession(currentUser.getUserId(), sessionId);
        return Result.success("设备会话已退出", null);
    }

    @DeleteMapping("/sessions")
    public Result<Void> revokeAll(HttpServletResponse response) {
        sessionService.revokeAll(currentUser.getUserId());
        clearRefreshCookie(response);
        return Result.success("所有设备会话已退出", null);
    }

    @PostMapping("/step-up")
    public Result<StepUpVO> stepUp(@Valid @RequestBody StepUpDTO dto) {
        rateLimitService.require("step-up", String.valueOf(currentUser.getUserId()), 10, Duration.ofMinutes(15));
        return Result.success(securityService.createStepUp(currentUser.getUserId(), dto.getPassword()));
    }

    @PutMapping("/email")
    public Result<Boolean> updateEmail(@Valid @RequestBody EmailUpdateDTO dto) {
        boolean mailSent = securityService.updateEmail(currentUser.getUserId(), dto.getEmail());
        return Result.success(mailSent ? "验证邮件已发送" : "邮箱已保存，当前实例未启用邮件发送", mailSent);
    }

    @PostMapping("/email/confirm")
    public Result<Void> confirmEmail(@Valid @RequestBody TokenDTO dto) {
        securityService.verifyEmail(dto.getToken());
        return Result.success("邮箱验证成功", null);
    }

    @PostMapping("/password/reset-request")
    public Result<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDTO dto,
                                             HttpServletRequest request) {
        rateLimitService.require("password-reset", rateLimitService.clientAddress(request) + ":" + dto.getEmail(), 5, Duration.ofHours(1));
        securityService.requestPasswordReset(dto.getEmail());
        return Result.success("如果该邮箱已验证，重置邮件将很快送达", null);
    }

    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody PasswordResetDTO dto) {
        securityService.resetPassword(dto.getToken(), dto.getNewPassword());
        return Result.success("密码已重置，请重新登录", null);
    }

    @PostMapping("/recovery-codes")
    public Result<List<String>> recoveryCodes(@Valid @RequestBody StepUpDTO dto) {
        return Result.success("请立即妥善保存，恢复码只显示一次",
                securityService.regenerateRecoveryCodes(currentUser.getUserId(), dto.getPassword()));
    }

    @PostMapping("/password/recover")
    public Result<Void> recoverWithCode(@Valid @RequestBody RecoveryCodeResetDTO dto,
                                        HttpServletRequest request) {
        rateLimitService.require("recovery-code", rateLimitService.clientAddress(request) + ":" + dto.getUsername(), 10, Duration.ofHours(1));
        securityService.recoverWithCode(dto.getUsername(), dto.getRecoveryCode(), dto.getNewPassword());
        return Result.success("密码已重置，请重新登录", null);
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> REFRESH_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst().orElse(null);
    }

    private void writeRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/v2/auth")
                .maxAge(Duration.ofDays(refreshDays))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/v2/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
