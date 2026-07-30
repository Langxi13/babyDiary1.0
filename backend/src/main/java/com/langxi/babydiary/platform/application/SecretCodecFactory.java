package com.langxi.babydiary.platform.application;

public interface SecretCodecFactory {
    SecretCodec create(String encryptionKey);
}
