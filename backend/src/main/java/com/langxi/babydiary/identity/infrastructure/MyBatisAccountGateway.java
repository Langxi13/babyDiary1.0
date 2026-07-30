package com.langxi.babydiary.identity.infrastructure;

import com.langxi.babydiary.identity.application.AccountGateway;
import com.langxi.babydiary.identity.domain.Account;
import com.langxi.babydiary.platform.application.BinaryUuid;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAccountGateway implements AccountGateway {
    private final IdentityMapper mapper;

    public MyBatisAccountGateway(IdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        return Optional.ofNullable(mapper.findByUsername(username)).map(this::account);
    }

    @Override
    public Optional<Account> findById(long accountId) {
        return Optional.ofNullable(mapper.findById(accountId)).map(this::account);
    }

    @Override
    public long insertAccount(UUID publicId, String username, String passwordHash) {
        IdentityMapper.AccountInsert row =
                new IdentityMapper.AccountInsert(
                        BinaryUuid.toBytes(publicId), username, passwordHash);
        mapper.insertAccount(row);
        if (row.getAccountId() == null)
            throw new IllegalStateException("Account insert returned no ID");
        return row.getAccountId();
    }

    @Override
    public long countAccounts() {
        return mapper.countAccounts();
    }

    @Override
    public void promoteToAdmin(long accountId) {
        mapper.promoteToAdmin(accountId);
    }

    @Override
    public java.util.List<SessionView> findSessions(long accountId) {
        return mapper.findSessions(accountId).stream()
                .map(
                        row ->
                                new SessionView(
                                        BinaryUuid.fromBytes(row.publicId()),
                                        row.deviceName(),
                                        row.userAgent(),
                                        row.ipAddress(),
                                        row.expiresAt(),
                                        row.lastSeenAt(),
                                        row.createdAt()))
                .toList();
    }

    @Override
    public Optional<UUID> findSessionId(byte[] refreshHash, long accountId, LocalDateTime now) {
        return Optional.ofNullable(mapper.findSessionId(refreshHash, accountId, now))
                .map(UUID::fromString);
    }

    @Override
    public boolean revokeSession(long accountId, UUID sessionId, LocalDateTime now) {
        return mapper.revokeSession(accountId, BinaryUuid.toBytes(sessionId), now) == 1;
    }

    @Override
    public void createSession(
            UUID publicId,
            long accountId,
            byte[] refreshHash,
            String deviceName,
            String userAgent,
            String ipAddress,
            LocalDateTime expiresAt) {
        mapper.insertSession(
                BinaryUuid.toBytes(publicId),
                accountId,
                refreshHash,
                deviceName,
                userAgent,
                ipAddress,
                expiresAt);
    }

    @Override
    public Optional<RefreshSession> findRefreshSession(byte[] refreshHash, LocalDateTime now) {
        return Optional.ofNullable(mapper.findRefreshSession(refreshHash, now))
                .map(row -> new RefreshSession(row.sessionId(), account(row), row.expiresAt()));
    }

    @Override
    public boolean rotateRefreshToken(
            long sessionId, byte[] previousHash, byte[] nextHash, LocalDateTime now) {
        return mapper.rotateRefreshToken(sessionId, previousHash, nextHash, now) == 1;
    }

    @Override
    public void revokeRefreshToken(byte[] refreshHash, LocalDateTime now) {
        mapper.revokeRefreshToken(refreshHash, now);
    }

    private Account account(IdentityMapper.AccountRow row) {
        return new Account(
                row.accountId(),
                BinaryUuid.fromBytes(row.publicId()),
                row.username(),
                row.passwordHash(),
                row.email(),
                row.emailVerified(),
                row.systemRole(),
                row.timezone(),
                row.tokenVersion(),
                row.status());
    }

    private Account account(IdentityMapper.RefreshSessionRow row) {
        return new Account(
                row.accountId(),
                BinaryUuid.fromBytes(row.publicId()),
                row.username(),
                row.passwordHash(),
                row.email(),
                row.emailVerified(),
                row.systemRole(),
                row.timezone(),
                row.tokenVersion(),
                row.status());
    }
}
