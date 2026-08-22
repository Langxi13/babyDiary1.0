package com.langxi.babydiary.space.application;

import com.langxi.babydiary.platform.application.AfterCommit;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.space.domain.SpaceSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpaceService implements SpaceAccess {
    private static final long DEFAULT_QUOTA = 5L * 1024 * 1024 * 1024;
    private final SpaceGateway spaces;
    private final SpaceAccessProjectionCache accessCache;

    public SpaceService(SpaceGateway spaces, SpaceAccessProjectionCache accessCache) {
        this.spaces = spaces;
        this.accessCache = accessCache;
    }

    public List<SpaceView> list(long accountId) {
        return spaces.findForAccount(accountId).stream().map(this::toView).toList();
    }

    @Transactional
    public SpaceView create(long accountId, String name, String defaultVisibility) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank())
            throw ApiException.badRequest("SPACE_NAME_REQUIRED", "空间名称不能为空");
        String visibility = "PRIVATE".equals(defaultVisibility) ? "PRIVATE" : "SHARED";
        UUID publicId = UUID.randomUUID();
        long spaceId =
                spaces.insert(publicId, normalizedName, accountId, visibility, DEFAULT_QUOTA);
        spaces.insertOwner(spaceId, accountId);
        spaces.insertStorageUsage(spaceId);
        return new SpaceView(
                publicId, normalizedName, "SHARED", "OWNER", visibility, DEFAULT_QUOTA, 0);
    }

    @Transactional
    public SpaceView update(UUID spaceId, long accountId, String name, String defaultVisibility) {
        SpaceContext context = requireMember(spaceId, accountId);
        if (!("OWNER".equals(context.role()) || "ADMIN".equals(context.role()))) {
            throw ApiException.forbidden("SPACE_MANAGE_FORBIDDEN", "只有空间管理员可以修改空间设置");
        }
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank())
            throw ApiException.badRequest("SPACE_NAME_REQUIRED", "空间名称不能为空");
        String visibility = "PRIVATE".equals(defaultVisibility) ? "PRIVATE" : "SHARED";
        if (!spaces.update(context.internalId(), normalizedName, visibility)) {
            throw ApiException.notFound("SPACE_NOT_FOUND", "空间不存在或无权访问");
        }
        AfterCommit.run(() -> accessCache.invalidateSpace(spaceId));
        SpaceContext fresh = spaces.findContext(spaceId, accountId).orElse(context);
        return new SpaceView(
                spaceId,
                normalizedName,
                context.type(),
                context.role(),
                visibility,
                fresh.storageQuotaBytes(),
                fresh.storageUsedBytes());
    }

    @Override
    public SpaceContext requireMember(UUID spaceId, long accountId) {
        return accessCache
                .find(spaceId, accountId)
                .orElseThrow(() -> ApiException.notFound("SPACE_NOT_FOUND", "空间不存在或无权访问"));
    }

    @Override
    public SpaceContext requireWriter(UUID spaceId, long accountId) {
        SpaceContext context = requireMember(spaceId, accountId);
        if ("VIEWER".equals(context.role())) {
            throw ApiException.forbidden("SPACE_WRITE_FORBIDDEN", "当前成员只能查看该空间");
        }
        return context;
    }

    private SpaceView toView(SpaceSummary space) {
        return new SpaceView(
                space.id(),
                space.name(),
                space.type(),
                space.role(),
                space.defaultVisibility(),
                space.storageQuotaBytes(),
                space.storageUsedBytes());
    }

    public record SpaceView(
            UUID id,
            String name,
            String type,
            String role,
            String defaultVisibility,
            long storageQuotaBytes,
            long storageUsedBytes) {}
}
