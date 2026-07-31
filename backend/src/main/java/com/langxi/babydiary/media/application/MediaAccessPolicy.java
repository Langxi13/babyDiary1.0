package com.langxi.babydiary.media.application;

import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.BinaryUuid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MediaAccessPolicy {
    private static final int ID_BATCH_SIZE = 500;
    private final MediaAccessRepository access;

    public MediaAccessPolicy(MediaAccessRepository access) {
        this.access = access;
    }

    public void require(UUID spaceId, UUID assetId, MediaAccessContext context) {
        byte[] space = BinaryUuid.toBytes(spaceId);
        byte[] asset = BinaryUuid.toBytes(assetId);
        byte[] parent =
                context.contextId() == null ? null : BinaryUuid.toBytes(context.contextId());
        MediaAccessRepository.AccessDecision row =
                switch (context.source()) {
                    case DIRECT -> access.direct(space, asset, context.accountId());
                    case DIARY -> access.diary(space, asset, parent, context.accountId());
                    case ALBUM -> access.album(space, asset, parent, context.accountId());
                    case ANNIVERSARY ->
                            access.anniversary(space, asset, parent, context.accountId());
                    case AVATAR -> access.avatar(space, asset, parent);
                    case SHARE -> access.share(space, asset, parent);
                };
        if (row == null || !row.canAccess()) {
            throw ApiException.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权访问");
        }
        if (row.protectedContent()) {
            if (context.source() == MediaAccessContext.Source.SHARE) {
                throw ApiException.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权访问");
            }
            if (!context.elevated()) {
                throw new ApiException(HttpStatus.LOCKED, "STEP_UP_REQUIRED", "请先完成二次验证");
            }
        }
    }

    public boolean isProtected(UUID spaceId, UUID assetId) {
        return Boolean.TRUE.equals(
                access.protectedAsset(BinaryUuid.toBytes(spaceId), BinaryUuid.toBytes(assetId)));
    }

    public Set<UUID> protectedAssets(UUID spaceId, List<UUID> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) return Set.of();
        List<UUID> distinct = assetIds.stream().distinct().toList();
        java.util.HashSet<UUID> result = new java.util.HashSet<>();
        for (int start = 0; start < distinct.size(); start += ID_BATCH_SIZE) {
            List<UUID> batch =
                    distinct.subList(start, Math.min(start + ID_BATCH_SIZE, distinct.size()));
            access
                    .protectedAssets(
                            BinaryUuid.toBytes(spaceId),
                            batch.stream().map(BinaryUuid::toBytes).toList())
                    .stream()
                    .map(BinaryUuid::fromBytes)
                    .forEach(result::add);
        }
        return Set.copyOf(result);
    }
}
