package com.langxi.babydiary.v3.identity.application;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository {
    Optional<Profile> find(long accountId);

    void update(long accountId, String username, String email, String timezone);

    void setAvatar(long accountId, long spaceId, long assetId);

    void clearAvatar(long accountId);

    void changePassword(long accountId, String passwordHash, LocalDateTime now);

    record Profile(long accountId, UUID id, String username, String passwordHash, String email,
                   boolean emailVerified, String role, String timezone, UUID avatarAssetId, UUID avatarSpaceId) {
    }
}
