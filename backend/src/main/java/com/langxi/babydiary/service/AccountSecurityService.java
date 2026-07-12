package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.StepUpVO;
import com.langxi.babydiary.entity.AccountToken;
import com.langxi.babydiary.entity.User;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AccountSecurityMapper;
import com.langxi.babydiary.mapper.UserMapper;
import com.langxi.babydiary.util.SecureTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AccountSecurityService {
    private final AccountSecurityMapper securityMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccountMailService mailService;
    private final StepUpTokenVerifier stepUpTokenVerifier;

    @Value("${app.auth.step-up-minutes:10}")
    private int stepUpMinutes;

    public AccountSecurityService(AccountSecurityMapper securityMapper,
                                  UserMapper userMapper,
                                  PasswordEncoder passwordEncoder,
                                  AccountMailService mailService,
                                  StepUpTokenVerifier stepUpTokenVerifier) {
        this.securityMapper = securityMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.stepUpTokenVerifier = stepUpTokenVerifier;
    }

    @Transactional
    public StepUpVO createStepUp(Integer userId, String password) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "当前密码不正确");
        }
        securityMapper.deleteAccountTokens(userId, "STEP_UP");
        String rawToken = SecureTokens.randomToken(32);
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(stepUpMinutes, ChronoUnit.MINUTES));
        insertToken(userId, "STEP_UP", rawToken, expiresAt);
        return new StepUpVO(rawToken, expiresAt);
    }

    public void requireStepUp(Integer userId, String rawToken) {
        stepUpTokenVerifier.require(userId, rawToken);
    }

    @Transactional
    public boolean updateEmail(Integer userId, String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        User existing = userMapper.findByEmail(normalized);
        if (existing != null && !existing.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        userMapper.updateEmail(userId, normalized, false);
        securityMapper.deleteAccountTokens(userId, "VERIFY_EMAIL");
        String rawToken = SecureTokens.randomToken(32);
        insertToken(userId, "VERIFY_EMAIL", rawToken,
                Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)));
        mailService.sendVerification(normalized, rawToken);
        return mailService.isEnabled();
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        AccountToken token = consumeAccountToken(rawToken, "VERIFY_EMAIL", ErrorCode.RECOVERY_TOKEN_INVALID);
        userMapper.markEmailVerified(token.getUserId());
    }

    @Transactional
    public void requestPasswordReset(String email) {
        User user = userMapper.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        if (user == null || !Boolean.TRUE.equals(user.getEmailVerified())) return;
        securityMapper.deleteAccountTokens(user.getUserId(), "RESET_PASSWORD");
        String rawToken = SecureTokens.randomToken(32);
        insertToken(user.getUserId(), "RESET_PASSWORD", rawToken,
                Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
        mailService.sendPasswordReset(user.getEmail(), rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        AccountToken token = consumeAccountToken(rawToken, "RESET_PASSWORD", ErrorCode.RECOVERY_TOKEN_INVALID);
        userMapper.updatePasswordAndIncrementTokenVersion(token.getUserId(), passwordEncoder.encode(newPassword));
        securityMapper.revokeAllSessions(token.getUserId());
        securityMapper.deleteAccountTokens(token.getUserId(), "STEP_UP");
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(Integer userId, String password) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "当前密码不正确");
        }
        List<String> codes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String code = formatRecoveryCode(SecureTokens.randomToken(12));
            codes.add(code);
            hashes.add(SecureTokens.sha256(normalizeRecoveryCode(code)));
        }
        securityMapper.deleteRecoveryCodes(userId);
        securityMapper.insertRecoveryCodes(userId, hashes);
        return codes;
    }

    @Transactional
    public void recoverWithCode(String username, String recoveryCode, String newPassword) {
        User user = userMapper.findByUsername(username.trim());
        if (user == null) throw new BusinessException(ErrorCode.RECOVERY_TOKEN_INVALID);
        String hash = SecureTokens.sha256(normalizeRecoveryCode(recoveryCode));
        if (securityMapper.consumeRecoveryCode(user.getUserId(), hash) != 1) {
            throw new BusinessException(ErrorCode.RECOVERY_TOKEN_INVALID);
        }
        userMapper.updatePasswordAndIncrementTokenVersion(user.getUserId(), passwordEncoder.encode(newPassword));
        securityMapper.revokeAllSessions(user.getUserId());
        securityMapper.deleteAccountTokens(user.getUserId(), "STEP_UP");
    }

    private AccountToken consumeAccountToken(String rawToken, String type, ErrorCode errorCode) {
        AccountToken token = securityMapper.findValidAccountTokenForUpdate(SecureTokens.sha256(rawToken), type);
        if (token == null || securityMapper.consumeAccountToken(token.getTokenId()) != 1) {
            throw new BusinessException(errorCode);
        }
        return token;
    }

    private void insertToken(Integer userId, String type, String rawToken, Timestamp expiresAt) {
        AccountToken token = new AccountToken();
        token.setUserId(userId);
        token.setType(type);
        token.setTokenHash(SecureTokens.sha256(rawToken));
        token.setExpiresAt(expiresAt);
        securityMapper.insertAccountToken(token);
    }

    private User requireUser(Integer userId) {
        User user = userMapper.findById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        return user;
    }

    private String formatRecoveryCode(String value) {
        String normalized = value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        while (normalized.length() < 16) normalized += "X";
        normalized = normalized.substring(0, 16);
        return normalized.substring(0, 4) + "-" + normalized.substring(4, 8) + "-"
                + normalized.substring(8, 12) + "-" + normalized.substring(12, 16);
    }

    private String normalizeRecoveryCode(String value) {
        return value == null ? "" : value.replace("-", "").trim().toUpperCase(Locale.ROOT);
    }
}
