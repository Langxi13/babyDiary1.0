package com.langxi.babydiary.identity.application;

import java.time.LocalDateTime;
import java.util.List;

public interface AccountRecoveryRepository {
    AccountEmail findByEmail(String email);

    AccountPassword findByUsername(String username);

    int updateEmail(long accountId, String email);

    void insertToken(long accountId, String type, byte[] tokenHash, LocalDateTime expiresAt);

    void deleteTokens(long accountId, String type);

    Token findTokenForUpdate(byte[] tokenHash, String type, LocalDateTime now);

    int consumeToken(long tokenId, LocalDateTime now);

    void verifyEmail(long accountId);

    void deleteRecoveryCodes(long accountId);

    void insertRecoveryCodes(long accountId, List<byte[]> hashes);

    int consumeRecoveryCode(long accountId, byte[] codeHash, LocalDateTime now);

    int updatePassword(long accountId, String passwordHash, LocalDateTime now);

    void revokeSessions(long accountId, LocalDateTime now);

    record AccountEmail(long accountId, String email, boolean emailVerified) {}

    record AccountPassword(long accountId, String passwordHash) {}

    record Token(long tokenId, long accountId) {}
}
