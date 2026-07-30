package com.langxi.babydiary.space.application;

import com.langxi.babydiary.space.domain.SpaceSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceGateway {
    List<SpaceSummary> findForAccount(long accountId);

    Optional<SpaceAccess.SpaceContext> findContext(UUID publicId, long accountId);

    long insert(UUID publicId, String name, long createdBy, String defaultVisibility, long quotaBytes);

    long insertPersonal(UUID publicId, String name, long ownerId, long quotaBytes);

    void insertOwner(long spaceId, long accountId);

    void insertStorageUsage(long spaceId);

    boolean update(long spaceId, String name, String defaultVisibility);
}
