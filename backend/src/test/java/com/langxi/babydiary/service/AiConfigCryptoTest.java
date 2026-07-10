package com.langxi.babydiary.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigCryptoTest {

    @Test
    void encryptsApiKeyWithoutStoringPlaintextAndDecryptsIt() {
        AiConfigCrypto crypto = new AiConfigCrypto("test-encryption-key-for-ai-config");

        String encrypted = crypto.encrypt("sk-test-secret");

        assertThat(encrypted).doesNotContain("sk-test-secret");
        assertThat(encrypted).startsWith("v1:");
        assertThat(crypto.decrypt(encrypted)).isEqualTo("sk-test-secret");
    }
}
