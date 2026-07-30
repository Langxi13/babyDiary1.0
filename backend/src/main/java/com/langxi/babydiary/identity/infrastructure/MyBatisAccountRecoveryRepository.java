package com.langxi.babydiary.identity.infrastructure;

import com.langxi.babydiary.identity.application.AccountRecoveryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAccountRecoveryRepository implements AccountRecoveryRepository {
    private final AccountRecoveryMapper mapper;

    public MyBatisAccountRecoveryRepository(AccountRecoveryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AccountEmail findByEmail(String email) {
        AccountRecoveryMapper.AccountEmailRow row = mapper.findByEmail(email);
        return row == null
                ? null
                : new AccountEmail(row.accountId(), row.email(), row.emailVerified());
    }

    @Override
    public AccountPassword findByUsername(String username) {
        AccountRecoveryMapper.AccountPasswordRow row = mapper.findByUsername(username);
        return row == null ? null : new AccountPassword(row.accountId(), row.passwordHash());
    }

    @Override
    public int updateEmail(long accountId, String email) {
        return mapper.updateEmail(accountId, email);
    }

    @Override
    public void insertToken(
            long accountId, String type, byte[] tokenHash, LocalDateTime expiresAt) {
        mapper.insertToken(accountId, type, tokenHash, expiresAt);
    }

    @Override
    public void deleteTokens(long accountId, String type) {
        mapper.deleteTokens(accountId, type);
    }

    @Override
    public Token findTokenForUpdate(byte[] tokenHash, String type, LocalDateTime now) {
        AccountRecoveryMapper.TokenRow row = mapper.findTokenForUpdate(tokenHash, type, now);
        return row == null ? null : new Token(row.tokenId(), row.accountId());
    }

    @Override
    public int consumeToken(long tokenId, LocalDateTime now) {
        return mapper.consumeToken(tokenId, now);
    }

    @Override
    public void verifyEmail(long accountId) {
        mapper.verifyEmail(accountId);
    }

    @Override
    public void deleteRecoveryCodes(long accountId) {
        mapper.deleteRecoveryCodes(accountId);
    }

    @Override
    public void insertRecoveryCodes(long accountId, List<byte[]> hashes) {
        mapper.insertRecoveryCodes(accountId, hashes);
    }

    @Override
    public int consumeRecoveryCode(long accountId, byte[] codeHash, LocalDateTime now) {
        return mapper.consumeRecoveryCode(accountId, codeHash, now);
    }

    @Override
    public int updatePassword(long accountId, String passwordHash, LocalDateTime now) {
        return mapper.updatePassword(accountId, passwordHash, now);
    }

    @Override
    public void revokeSessions(long accountId, LocalDateTime now) {
        mapper.revokeSessions(accountId, now);
    }
}
