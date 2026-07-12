package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.util.AesGcmSecretCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InvitationCodeCrypto {
    private final AesGcmSecretCodec codec;

    public InvitationCodeCrypto(@Value("${app.invitation.encryption-key}") String encryptionKey) {
        if (encryptionKey == null || encryptionKey.length() < 32) {
            throw new IllegalArgumentException("INVITATION_CODE_ENCRYPTION_KEY must contain at least 32 characters");
        }
        this.codec = new AesGcmSecretCodec(encryptionKey);
    }

    public String encrypt(String invitationCode) {
        try {
            return codec.encrypt(invitationCode);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    public String decrypt(String encryptedCode) {
        try {
            return codec.decrypt(encryptedCode);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.INTERNAL_ERROR, "邀请码安全配置不可用");
    }
}
