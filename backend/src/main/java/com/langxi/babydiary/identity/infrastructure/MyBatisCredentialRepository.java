package com.langxi.babydiary.identity.infrastructure;

import com.langxi.babydiary.identity.application.CredentialRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisCredentialRepository implements CredentialRepository {
    private final CredentialMapper mapper;

    public MyBatisCredentialRepository(CredentialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<String> findPasswordHash(long accountId) {
        return Optional.ofNullable(mapper.findPasswordHash(accountId));
    }

    @Override
    public void changePassword(long accountId, String passwordHash, LocalDateTime now) {
        mapper.updatePassword(accountId, passwordHash, now);
        mapper.revokeSessions(accountId, now);
    }
}
