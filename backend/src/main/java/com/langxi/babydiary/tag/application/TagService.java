package com.langxi.babydiary.tag.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.ReadCache;
import com.langxi.babydiary.platform.application.ReadCacheInvalidator;
import com.langxi.babydiary.space.application.SpaceAccess;
import com.langxi.babydiary.tag.domain.Tag;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final TypeReference<List<Tag>> TAG_LIST = new TypeReference<>() {};
    private final SpaceAccess spaces;
    private final TagRepository tags;
    private final ReadCache cache;
    private final ReadCacheInvalidator cacheInvalidator;

    public TagService(
            SpaceAccess spaces,
            TagRepository tags,
            ReadCache cache,
            ReadCacheInvalidator cacheInvalidator) {
        this.spaces = spaces;
        this.tags = tags;
        this.cache = cache;
        this.cacheInvalidator = cacheInvalidator;
    }

    public List<Tag> list(UUID spaceId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return cache.get(
                ReadCacheInvalidator.TAGS,
                spaceId,
                accountId,
                "list",
                Duration.ofMinutes(5),
                TAG_LIST,
                () -> tags.findForSpace(space.internalId()));
    }

    @Transactional
    public Tag create(UUID spaceId, long accountId, String name, String color) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank())
            throw ApiException.badRequest("TAG_NAME_REQUIRED", "标签名称不能为空");
        if (normalizedName.length() > 32)
            throw ApiException.badRequest("TAG_NAME_TOO_LONG", "标签名称不能超过32个字符");
        String normalizedColor = color == null || color.isBlank() ? null : color.trim();
        if (normalizedColor != null && !COLOR.matcher(normalizedColor).matches()) {
            throw ApiException.badRequest("TAG_COLOR_INVALID", "标签颜色格式无效");
        }
        UUID publicId = UUID.randomUUID();
        try {
            long id =
                    tags.insert(
                            new TagRepository.NewTag(
                                    publicId,
                                    space.internalId(),
                                    normalizedName,
                                    normalizedColor,
                                    accountId));
            Tag created = new Tag(id, publicId, spaceId, normalizedName, normalizedColor);
            cacheInvalidator.tags(spaceId);
            return created;
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.CONFLICT, "TAG_NAME_EXISTS", "当前空间已存在同名标签");
        }
    }
}
