package com.langxi.babydiary.identity.application;

import com.langxi.babydiary.identity.domain.Account;
import com.langxi.babydiary.platform.application.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthenticationService {
    private final AccountGateway accounts;
    private final AccessTokenCodec accessTokens;
    private final PasswordEncoder passwords;
    private final SecureRandom random;
    private final Clock clock;
    private final int refreshDays;

    @Autowired
    public AuthenticationService(AccountGateway accounts,
                                 AccessTokenCodec accessTokens,
                                 PasswordEncoder passwords,
                                 @Value("${app.auth.refresh-days:30}") int refreshDays) {
        this(accounts, accessTokens, passwords, refreshDays, new SecureRandom(), Clock.systemUTC());
    }

    AuthenticationService(AccountGateway accounts, AccessTokenCodec accessTokens, PasswordEncoder passwords,
                          int refreshDays, SecureRandom random, Clock clock) {
        this.accounts = accounts;
        this.accessTokens = accessTokens;
        this.passwords = passwords;
        this.refreshDays = refreshDays;
        this.random = random;
        this.clock = clock;
    }

    @Transactional
    public Session login(String username, String password, Device device) {
        Account account = accounts.findByUsername(username.trim())
                .filter(Account::active)
                .orElseThrow(this::invalidCredentials);
        if (!passwords.matches(password, account.passwordHash())) throw invalidCredentials();

        String refreshToken = refreshToken();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusDays(refreshDays);
        accounts.createSession(UUID.randomUUID(), account.id(), hash(refreshToken),
                trim(device.deviceName(), 160), trim(device.userAgent(), 500), trim(device.ipAddress(), 64), expiresAt);
        return session(account, refreshToken);
    }

    @Transactional
    public Session refresh(String currentRefreshToken) {
        byte[] previousHash = hash(currentRefreshToken);
        LocalDateTime now = LocalDateTime.now(clock);
        AccountGateway.RefreshSession session = accounts.findRefreshSession(previousHash, now)
                .filter(value -> value.account().active())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "REFRESH_TOKEN_INVALID", "登录状态已失效，请重新登录"));
        String nextRefreshToken = refreshToken();
        if (!accounts.rotateRefreshToken(session.sessionId(), previousHash, hash(nextRefreshToken), now)) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_REUSED", "登录状态已失效，请重新登录");
        }
        return session(session.account(), nextRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        accounts.revokeRefreshToken(hash(refreshToken), LocalDateTime.now(clock));
    }

    public java.util.List<SessionView> sessions(long accountId, String refreshToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        UUID current = refreshToken == null || refreshToken.isBlank() ? null
                : accounts.findSessionId(hash(refreshToken), accountId, now).orElse(null);
        return accounts.findSessions(accountId).stream().map(value -> new SessionView(value.id(), value.deviceName(),
                value.userAgent(), value.ipAddress(), value.expiresAt(), value.lastSeenAt(), value.createdAt(),
                value.id().equals(current))).toList();
    }

    @Transactional
    public void revokeSession(long accountId, UUID sessionId) {
        if (!accounts.revokeSession(accountId, sessionId, LocalDateTime.now(clock))) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "登录设备不存在或已经退出");
        }
    }

    private Session session(Account account, String refreshToken) {
        AccessTokenCodec.IssuedToken accessToken = accessTokens.issue(account);
        return new Session(account.publicId(), account.username(), account.email(), account.systemRole(),
                account.timezone(), accessToken.value(), accessToken.expiresAt().getEpochSecond(), refreshToken);
    }

    private String refreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] hash(String value) {
        if (value == null || value.isBlank()) throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "REFRESH_TOKEN_MISSING", "缺少登录凭证");
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS", "用户名或密码错误");
    }

    private String trim(String value, int length) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= length ? normalized : normalized.substring(0, length);
    }

    public record Device(String deviceName, String userAgent, String ipAddress) {
    }

    public record Session(UUID accountId, String username, String email, String role, String timezone,
                          String accessToken, long accessExpiresAt, String refreshToken) {
    }

    public record SessionView(UUID id, String deviceName, String userAgent, String ipAddress,
                              LocalDateTime expiresAt, LocalDateTime lastSeenAt, LocalDateTime createdAt,
                              boolean current) {
    }
}
