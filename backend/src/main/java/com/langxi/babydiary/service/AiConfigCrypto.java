package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.util.AesGcmSecretCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiConfigCrypto {
    private final AesGcmSecretCodec codec;

    public AiConfigCrypto(@Value("${ai.config.encryption-key}") String encryptionKey) {
        this.codec = new AesGcmSecretCodec(encryptionKey);
    }

    public String encrypt(String plaintext) {
        try {
            return codec.encrypt(plaintext);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AI_CONFIG_INVALID, "AI密钥加密失败");
        }
    }

    public String decrypt(String encryptedValue) {
        try {
            return codec.decrypt(encryptedValue);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AI_CONFIG_INVALID, "AI密钥解密失败，请重新配置API Key");
        }
    }
}
