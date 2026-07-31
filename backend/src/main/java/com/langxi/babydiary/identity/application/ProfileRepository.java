package com.langxi.babydiary.identity.application;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository {
    Optional<Profile> find(long accountId);

    void update(long accountId, String username, String email, String timezone);

    void setAvatar(long accountId, long spaceId, long assetId);

    void clearAvatar(long accountId);

    record Profile(
            long accountId,
            UUID id,
            String username,
            String email,
            boolean emailVerified,
            String role,
            String timezone,
            LocalDateTime createdAt,
            UUID avatarAssetId,
            UUID avatarSpaceId,
            String avatarVariantType,
            String avatarVariantProfile) {}
}
