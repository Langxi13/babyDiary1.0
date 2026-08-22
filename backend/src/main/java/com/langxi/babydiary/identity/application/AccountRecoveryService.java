package com.langxi.babydiary.identity.application;

import com.langxi.babydiary.platform.application.AfterCommit;
import com.langxi.babydiary.platform.application.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountRecoveryService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final AccountRecoveryRepository mapper;
    private final ProfileService profiles;
    private final CredentialRepository credentials;
    private final PasswordEncoder passwords;
    private final AccountMailService mail;
    private final AuthenticationProjectionCache authenticationCache;
    private final SecureRandom random = new SecureRandom();

    public AccountRecoveryService(
            AccountRecoveryRepository mapper,
            ProfileService profiles,
            CredentialRepository credentials,
            PasswordEncoder passwords,
            AccountMailService mail,
            AuthenticationProjectionCache authenticationCache) {
        this.mapper = mapper;
        this.profiles = profiles;
        this.credentials = credentials;
        this.passwords = passwords;
        this.mail = mail;
        this.authenticationCache = authenticationCache;
    }

    @Transactional
    public EmailUpdate updateEmail(long accountId, String email) {
        String value = normalizeEmail(email);
        try {
            if (mapper.updateEmail(accountId, value) != 1) {
                throw ApiException.notFound("ACCOUNT_NOT_FOUND", "账户不存在");
            }
        } catch (DuplicateKeyException exception) {
            throw ApiException.conflict("EMAIL_EXISTS", "该邮箱已被使用");
        }
        mapper.deleteTokens(accountId, "EMAIL_VERIFY");
        String token = token();
        mapper.insertToken(accountId, "EMAIL_VERIFY", hash(token), now().plusHours(24));
        mail.verification(value, token);
        return new EmailUpdate(profiles.profile(accountId), mail.enabled());
    }

    @Transactional
    public void confirmEmail(String token) {
        AccountRecoveryRepository.Token row = token(token, "EMAIL_VERIFY");
        mapper.verifyEmail(row.accountId());
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String value;
        try {
            value = normalizeEmail(email);
        } catch (ApiException ignored) {
            return;
        }
        AccountRecoveryRepository.AccountEmail account = mapper.findByEmail(value);
        if (account == null || !account.emailVerified()) return;
        mapper.deleteTokens(account.accountId(), "PASSWORD_RESET");
        String token = token();
        mapper.insertToken(
                account.accountId(), "PASSWORD_RESET", hash(token), now().plusMinutes(30));
        mail.passwordReset(value, token);
    }

    @Transactional
    public void resetPassword(String token, String password) {
        AccountRecoveryRepository.Token row = token(token, "PASSWORD_RESET");
        setPassword(row.accountId(), password);
    }

    @Transactional
    public List<String> recoveryCodes(long accountId, String password) {
        String passwordHash =
                credentials
                        .findPasswordHash(accountId)
                        .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "账户不存在"));
        if (!passwords.matches(password, passwordHash)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "PASSWORD_INVALID", "当前密码错误");
        }
        List<String> values = new ArrayList<>();
        List<byte[]> hashes = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            String value = recoveryCode();
            values.add(value);
            hashes.add(hash(normalizeCode(value)));
        }
        mapper.deleteRecoveryCodes(accountId);
        mapper.insertRecoveryCodes(accountId, hashes);
        return values;
    }

    @Transactional
    public void recover(String username, String code, String password) {
        AccountRecoveryRepository.AccountPassword account =
                mapper.findByUsername(username == null ? "" : username.trim());
        if (account == null
                || mapper.consumeRecoveryCode(account.accountId(), hash(normalizeCode(code)), now())
                        != 1) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "RECOVERY_CODE_INVALID", "恢复码无效或已使用");
        }
        setPassword(account.accountId(), password);
    }

    private AccountRecoveryRepository.Token token(String raw, String type) {
        if (raw == null || raw.isBlank()) throw tokenInvalid();
        LocalDateTime now = now();
        AccountRecoveryRepository.Token row = mapper.findTokenForUpdate(hash(raw), type, now);
        if (row == null || mapper.consumeToken(row.tokenId(), now) != 1) throw tokenInvalid();
        return row;
    }

    private void setPassword(long accountId, String password) {
        if (password == null || password.length() < 8 || password.length() > 200) {
            throw ApiException.badRequest("PASSWORD_WEAK", "新密码长度需为8至200个字符");
        }
        LocalDateTime now = now();
        if (mapper.updatePassword(accountId, passwords.encode(password), now) != 1)
            throw tokenInvalid();
        mapper.revokeSessions(accountId, now);
        AfterCommit.run(() -> authenticationCache.invalidate(accountId));
    }

    private String normalizeEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 255 || !EMAIL.matcher(email).matches()) {
            throw ApiException.badRequest("EMAIL_INVALID", "邮箱格式无效");
        }
        return email;
    }

    private String token() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String recoveryCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder value = new StringBuilder(19);
        for (int index = 0; index < 16; index++) {
            if (index > 0 && index % 4 == 0) value.append('-');
            value.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return value.toString();
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.replace("-", "").trim().toUpperCase(Locale.ROOT);
    }

    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private ApiException tokenInvalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "RECOVERY_TOKEN_INVALID", "验证链接无效或已过期");
    }

    public record EmailUpdate(ProfileService.ProfileView profile, boolean mailSent) {}
}
