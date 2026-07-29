package com.langxi.babydiary.v3.identity.infrastructure;

import com.langxi.babydiary.v3.identity.application.InvitationCodeRepository;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisInvitationCodeRepository implements InvitationCodeRepository {
    private final InvitationCodeMapper mapper;

    public MyBatisInvitationCodeRepository(InvitationCodeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String findEncrypted() {
        return mapper.findEncrypted();
    }

    @Override public String findEncryptedForUpdate() { return mapper.findEncryptedForUpdate(); }

    @Override
    public void upsert(String encryptedCode, Long updatedBy) {
        mapper.upsert(encryptedCode, updatedBy);
    }
}
