package com.langxi.babydiary.identity.infrastructure;

import com.langxi.babydiary.identity.application.ProfileRepository;
import com.langxi.babydiary.platform.application.BinaryUuid;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MyBatisProfileRepository implements ProfileRepository {
    private final ProfileMapper mapper;

    public MyBatisProfileRepository(ProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Profile> find(long accountId) {
        return Optional.ofNullable(mapper.find(accountId)).map(row -> new Profile(row.accountId(),
                BinaryUuid.fromBytes(row.publicId()), row.username(), row.passwordHash(), row.email(),
                row.emailVerified(), row.systemRole(), row.timezone(),
                row.avatarPublicId() == null ? null : BinaryUuid.fromBytes(row.avatarPublicId()),
                row.avatarSpacePublicId() == null ? null : BinaryUuid.fromBytes(row.avatarSpacePublicId()),
                row.avatarVariantType(), row.avatarVariantProfile()));
    }

    @Override
    public void update(long accountId, String username, String email, String timezone) {
        mapper.update(accountId, username, email, timezone);
    }

    @Override
    public void setAvatar(long accountId, long spaceId, long assetId) {
        mapper.setAvatar(accountId, spaceId, assetId);
    }

    @Override
    public void clearAvatar(long accountId) {
        mapper.clearAvatar(accountId);
    }

    @Override
    public void changePassword(long accountId, String passwordHash, LocalDateTime now) {
        mapper.updatePassword(accountId, passwordHash, now);
        mapper.revokeSessions(accountId, now);
    }
}
