package com.langxi.babydiary.identity.application;

public interface InvitationCodeRepository {
    String findEncrypted();

    String findEncryptedForUpdate();

    void upsert(String encryptedCode, Long updatedBy);
}
