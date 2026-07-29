package com.langxi.babydiary.v3.space.application;

import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.space.domain.SpaceSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SpaceService implements SpaceAccess {
    private static final long DEFAULT_QUOTA = 5L * 1024 * 1024 * 1024;
    private final SpaceGateway spaces;

    public SpaceService(SpaceGateway spaces) {
        this.spaces = spaces;
    }

    public List<SpaceSummary> list(long accountId) {
        return spaces.findForAccount(accountId);
    }

    @Transactional
    public SpaceSummary create(long accountId, String name, String defaultVisibility) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) throw V3Exception.badRequest("SPACE_NAME_REQUIRED", "空间名称不能为空");
        String visibility = "PRIVATE".equals(defaultVisibility) ? "PRIVATE" : "SHARED";
        UUID publicId = UUID.randomUUID();
        long spaceId = spaces.insert(publicId, normalizedName, accountId, visibility, DEFAULT_QUOTA);
        spaces.insertOwner(spaceId, accountId);
        spaces.insertStorageUsage(spaceId);
        return new SpaceSummary(publicId, normalizedName, "SHARED", "OWNER", visibility, DEFAULT_QUOTA, 0);
    }

    @Transactional
    public SpaceSummary update(UUID spaceId, long accountId, String name, String defaultVisibility) {
        SpaceContext context = requireMember(spaceId, accountId);
        if (!("OWNER".equals(context.role()) || "ADMIN".equals(context.role()))) {
            throw V3Exception.forbidden("SPACE_MANAGE_FORBIDDEN", "只有空间管理员可以修改空间设置");
        }
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) throw V3Exception.badRequest("SPACE_NAME_REQUIRED", "空间名称不能为空");
        String visibility = "PRIVATE".equals(defaultVisibility) ? "PRIVATE" : "SHARED";
        if (!spaces.update(context.internalId(), normalizedName, visibility)) {
            throw V3Exception.notFound("SPACE_NOT_FOUND", "空间不存在或无权访问");
        }
        return new SpaceSummary(spaceId, normalizedName, context.type(), context.role(), visibility,
                context.storageQuotaBytes(), context.storageUsedBytes());
    }

    @Override
    public SpaceContext requireMember(UUID spaceId, long accountId) {
        return spaces.findContext(spaceId, accountId)
                .orElseThrow(() -> V3Exception.notFound("SPACE_NOT_FOUND", "空间不存在或无权访问"));
    }

    @Override
    public SpaceContext requireWriter(UUID spaceId, long accountId) {
        SpaceContext context = requireMember(spaceId, accountId);
        if ("VIEWER".equals(context.role())) {
            throw V3Exception.forbidden("SPACE_WRITE_FORBIDDEN", "当前成员只能查看该空间");
        }
        return context;
    }
}
