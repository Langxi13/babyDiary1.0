package com.langxi.babydiary.identity.application;

import com.langxi.babydiary.identity.domain.Account;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AccountGateway {
    Optional<Account> findByUsername(String username);

    Optional<Account> findById(long accountId);

    long insertAccount(UUID publicId, String username, String passwordHash);

    long countAccounts();

    void promoteToAdmin(long accountId);

    java.util.List<SessionView> findSessions(long accountId);

    Optional<UUID> findSessionId(byte[] refreshHash, long accountId, LocalDateTime now);

    boolean revokeSession(long accountId, UUID sessionId, LocalDateTime now);

    void createSession(UUID publicId, long accountId, byte[] refreshHash, String deviceName,
                       String userAgent, String ipAddress, LocalDateTime expiresAt);

    Optional<RefreshSession> findRefreshSession(byte[] refreshHash, LocalDateTime now);

    boolean rotateRefreshToken(long sessionId, byte[] previousHash, byte[] nextHash, LocalDateTime now);

    void revokeRefreshToken(byte[] refreshHash, LocalDateTime now);

    record RefreshSession(long sessionId, Account account, LocalDateTime expiresAt) {
    }

    record SessionView(UUID id, String deviceName, String userAgent, String ipAddress, LocalDateTime expiresAt,
                       LocalDateTime lastSeenAt, LocalDateTime createdAt) {
    }
}
