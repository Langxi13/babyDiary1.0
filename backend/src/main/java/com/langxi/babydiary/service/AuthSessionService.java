package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.AuthSessionVO;
import com.langxi.babydiary.dto.LoginVO;
import com.langxi.babydiary.dto.UserVO;
import com.langxi.babydiary.entity.AuthSession;
import com.langxi.babydiary.entity.User;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AccountSecurityMapper;
import com.langxi.babydiary.mapper.UserMapper;
import com.langxi.babydiary.security.JwtTokenProvider;
import com.langxi.babydiary.util.SecureTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AuthSessionService {
    private final AccountSecurityMapper securityMapper;
    private final UserMapper userMapper;
    private final JwtTokenProvider tokenProvider;

    @Value("${app.auth.refresh-days:30}")
    private int refreshDays;

    public AuthSessionService(AccountSecurityMapper securityMapper,
                              UserMapper userMapper,
                              JwtTokenProvider tokenProvider) {
        this.securityMapper = securityMapper;
        this.userMapper = userMapper;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public SessionLogin createSession(User user, String deviceName, String userAgent, String ipAddress) {
        String refreshToken = SecureTokens.randomToken(48);
        AuthSession session = new AuthSession();
        session.setPublicId(UUID.randomUUID().toString());
        session.setUserId(user.getUserId());
        session.setRefreshTokenHash(SecureTokens.sha256(refreshToken));
        session.setDeviceName(normalizeDeviceName(deviceName, userAgent));
        session.setUserAgent(limit(userAgent, 500));
        session.setIpAddress(limit(ipAddress, 64));
        session.setExpiresAt(nextExpiry());
        securityMapper.insertSession(session);
        return new SessionLogin(loginVO(user), refreshToken, session.getPublicId());
    }

    @Transactional
    public SessionLogin refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新会话已失效，请重新登录");
        }
        String previousHash = SecureTokens.sha256(refreshToken);
        AuthSession session = securityMapper.findSessionByTokenForUpdate(previousHash);
        if (session == null || session.getRevokedAt() != null || session.getExpiresAt().before(Timestamp.from(Instant.now()))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新会话已失效，请重新登录");
        }
        User user = userMapper.findById(session.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        String nextToken = SecureTokens.randomToken(48);
        Timestamp expiresAt = nextExpiry();
        if (securityMapper.rotateSession(session.getSessionId(), previousHash, SecureTokens.sha256(nextToken), expiresAt) != 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新会话已被使用，请重新登录");
        }
        return new SessionLogin(loginVO(user), nextToken, session.getPublicId());
    }

    public List<AuthSessionVO> listSessions(Integer userId, String currentRefreshToken) {
        String currentId = null;
        if (currentRefreshToken != null && !currentRefreshToken.isBlank()) {
            AuthSession current = securityMapper.findSessionByToken(SecureTokens.sha256(currentRefreshToken));
            currentId = current == null ? null : current.getPublicId();
        }
        String finalCurrentId = currentId;
        return securityMapper.listActiveSessions(userId).stream()
                .map(session -> AuthSessionVO.from(session, finalCurrentId))
                .toList();
    }

    public void revokeSession(Integer userId, String publicId) {
        if (securityMapper.revokeSession(userId, publicId) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在或已失效");
        }
    }

    public void revokeAll(Integer userId) {
        securityMapper.revokeAllSessions(userId);
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            securityMapper.revokeByToken(SecureTokens.sha256(refreshToken));
        }
    }

    @Scheduled(cron = "${app.auth.session-purge-cron:0 40 3 * * *}")
    @Transactional
    public void purgeExpiredSecurityData() {
        securityMapper.purgeOldSessions();
        securityMapper.purgeExpiredAccountTokens();
    }

    private LoginVO loginVO(User user) {
        LoginVO vo = new LoginVO();
        vo.setToken(tokenProvider.generateAccessToken(user.getUsername(), user.getUserId(), user.getTokenVersion()));
        vo.setExpiresIn(tokenProvider.getAccessExpiration());
        vo.setUserInfo(UserVO.fromEntity(user));
        return vo;
    }

    private Timestamp nextExpiry() {
        return Timestamp.from(Instant.now().plus(refreshDays, ChronoUnit.DAYS));
    }

    private String normalizeDeviceName(String deviceName, String userAgent) {
        if (deviceName != null && !deviceName.isBlank()) {
            return limit(deviceName.trim(), 160);
        }
        if (userAgent == null || userAgent.isBlank()) return "未知设备";
        String platform = userAgent.contains("iPhone") || userAgent.contains("iPad") ? "iOS"
                : userAgent.contains("Android") ? "Android"
                : userAgent.contains("Windows") ? "Windows"
                : userAgent.contains("Macintosh") ? "macOS" : "浏览器";
        return platform + " 设备";
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record SessionLogin(LoginVO login, String refreshToken, String sessionPublicId) {}
}
