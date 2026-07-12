package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationCodeCryptoTest {
    private static final String KEY = "invitation-code-test-key-000000000000000000000000";

    @Test
    void encryptsAndDecryptsWithoutPersistingPlaintext() {
        InvitationCodeCrypto crypto = new InvitationCodeCrypto(KEY);

        String encrypted = crypto.encrypt("private-invitation-code");

        assertThat(encrypted).startsWith("v1:").doesNotContain("private-invitation-code");
        assertThat(crypto.decrypt(encrypted)).isEqualTo("private-invitation-code");
    }

    @Test
    void rejectsCiphertextEncryptedWithAnotherKey() {
        InvitationCodeCrypto first = new InvitationCodeCrypto(KEY);
        InvitationCodeCrypto second = new InvitationCodeCrypto("another-invitation-key-0000000000000000000000000");

        assertThatThrownBy(() -> second.decrypt(first.encrypt("private-code")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode()));
    }

    @Test
    void rejectsShortEncryptionKeysAtStartup() {
        assertThatThrownBy(() -> new InvitationCodeCrypto("too-short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVITATION_CODE_ENCRYPTION_KEY");
    }
}
