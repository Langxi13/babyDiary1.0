package com.langxi.babydiary.space.application;

import java.util.UUID;

public interface SpaceAccess {
    SpaceContext requireMember(UUID spaceId, long accountId);

    SpaceContext requireWriter(UUID spaceId, long accountId);

    record SpaceContext(
            long internalId,
            UUID publicId,
            String role,
            String type,
            String defaultVisibility,
            long storageQuotaBytes,
            long storageUsedBytes) {}
}
