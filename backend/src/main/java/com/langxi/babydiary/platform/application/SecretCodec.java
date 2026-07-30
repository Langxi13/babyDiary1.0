package com.langxi.babydiary.platform.application;

public interface SecretCodec {
    String encrypt(String plaintext);

    String decrypt(String encryptedValue);
}
