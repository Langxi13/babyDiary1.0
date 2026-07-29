package com.langxi.babydiary.v3.identity.application;

public interface InvitationCodeRepository {
    String findEncrypted();

    String findEncryptedForUpdate();

    void upsert(String encryptedCode, Long updatedBy);
}
