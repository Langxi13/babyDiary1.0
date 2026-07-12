package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.InvitationCodeVO;
import com.langxi.babydiary.entity.SystemInvitationConfig;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.SystemInvitationConfigMapper;
import com.langxi.babydiary.util.SecureTokens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Service
public class InvitationCodeService implements ApplicationRunner {
    private final SystemInvitationConfigMapper configMapper;
    private final InvitationCodeCrypto crypto;
    private final StepUpTokenVerifier stepUpTokenVerifier;
    private final String bootstrapCode;

    public InvitationCodeService(SystemInvitationConfigMapper configMapper,
                                 InvitationCodeCrypto crypto,
                                 StepUpTokenVerifier stepUpTokenVerifier,
                                 @Value("${app.invitation.bootstrap-code:}") String bootstrapCode) {
        this.configMapper = configMapper;
        this.crypto = crypto;
        this.stepUpTokenVerifier = stepUpTokenVerifier;
        this.bootstrapCode = bootstrapCode == null ? "" : bootstrapCode.trim();
    }

    @Override
    public void run(ApplicationArguments arguments) {
        initializeFromBootstrap();
    }

    public void initializeFromBootstrap() {
        SystemInvitationConfig existing = configMapper.findConfig();
        if (existing != null) {
            decryptRequired(existing);
            return;
        }
        if (bootstrapCode.isBlank()) {
            throw new IllegalStateException(
                    "System invitation code is not initialized; provide INVITATION_CODE for the first startup");
        }

        SystemInvitationConfig config = new SystemInvitationConfig();
        config.setConfigId(1);
        config.setEncryptedCode(crypto.encrypt(bootstrapCode));
        configMapper.insertIfAbsent(config);
        if (configMapper.findConfig() == null) {
            throw new IllegalStateException("System invitation code initialization failed");
        }
        log.info("系统邀请码已完成加密初始化");
    }

    public boolean matches(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        SystemInvitationConfig config = configMapper.findConfigForShare();
        String currentCode = decryptRequired(config);
        return MessageDigest.isEqual(
                currentCode.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    public InvitationCodeVO getVisibleCode(Integer adminUserId, String stepUpToken) {
        stepUpTokenVerifier.require(adminUserId, stepUpToken);
        SystemInvitationConfig config = requireConfig();
        return new InvitationCodeVO(decryptRequired(config), config.getUpdatedAt());
    }

    @Transactional
    public InvitationCodeVO rotate(Integer adminUserId, String stepUpToken) {
        stepUpTokenVerifier.require(adminUserId, stepUpToken);
        String invitationCode = SecureTokens.randomToken(24);
        SystemInvitationConfig config = new SystemInvitationConfig();
        config.setConfigId(1);
        config.setEncryptedCode(crypto.encrypt(invitationCode));
        config.setUpdatedBy(adminUserId);
        configMapper.upsertConfig(config);
        SystemInvitationConfig updated = requireConfig();
        log.info("系统邀请码已由管理员 userId={} 轮换", adminUserId);
        return new InvitationCodeVO(invitationCode, updated.getUpdatedAt());
    }

    private SystemInvitationConfig requireConfig() {
        SystemInvitationConfig config = configMapper.findConfig();
        if (config == null) {
            throw unavailable();
        }
        return config;
    }

    private String decryptRequired(SystemInvitationConfig config) {
        if (config == null || config.getEncryptedCode() == null || config.getEncryptedCode().isBlank()) {
            throw unavailable();
        }
        String invitationCode = crypto.decrypt(config.getEncryptedCode());
        if (invitationCode == null || invitationCode.isBlank()) {
            throw unavailable();
        }
        return invitationCode;
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.INTERNAL_ERROR, "邀请码配置不可用");
    }
}
