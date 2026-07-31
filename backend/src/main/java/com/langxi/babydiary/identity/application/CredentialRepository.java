package com.langxi.babydiary.identity.application;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CredentialRepository {
    Optional<String> findPasswordHash(long accountId);

    void changePassword(long accountId, String passwordHash, LocalDateTime now);
}
