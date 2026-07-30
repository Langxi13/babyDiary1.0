package com.langxi.babydiary.album.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.langxi.babydiary.album.domain.AlbumCatalog;
import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.MediaAccessPolicy;
import com.langxi.babydiary.media.application.MediaRepository;
import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.ReadCache;
import com.langxi.babydiary.platform.application.ReadCacheInvalidator;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlbumService {
    private static final TypeReference<AlbumCatalog> CATALOG = new TypeReference<>() {};
    private static final TypeReference<AlbumCatalog.Detail> DETAIL = new TypeReference<>() {};
    private final SpaceAccess spaces;
    private final AlbumRepository albums;
    private final MediaRepository media;
    private final MediaAccessPolicy mediaAccess;
    private final ReadCache cache;
    private final ReadCacheInvalidator cacheInvalidator;

    public AlbumService(
            SpaceAccess spaces,
            AlbumRepository albums,
            MediaRepository media,
            MediaAccessPolicy mediaAccess,
            ReadCache cache,
            ReadCacheInvalidator cacheInvalidator) {
        this.spaces = spaces;
        this.albums = albums;
        this.media = media;
        this.mediaAccess = mediaAccess;
        this.cache = cache;
        this.cacheInvalidator = cacheInvalidator;
    }

    public AlbumCatalog catalog(UUID spaceId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return cache.get(
                ReadCacheInvalidator.ALBUM_METADATA,
                spaceId,
                accountId,
                "catalog",
                Duration.ofMinutes(2),
                CATALOG,
                () -> cacheSafe(catalog(space.internalId(), accountId)));
    }

    private AlbumCatalog catalog(long spaceId, long accountId) {
        List<AlbumCatalog.Group> groups = new ArrayList<>();
        groups.add(
                new AlbumCatalog.Group(
                        null,
                        "SYSTEM",
                        "默认相册",
                        List.of(
                                new AlbumCatalog.Album(
                                        null,
                                        null,
                                        "all",
                                        "SYSTEM",
                                        "所有图片",
                                        "",
                                        null,
                                        null,
                                        null,
                                        albums.countLibraryImages(spaceId, accountId),
                                        null),
                                new AlbumCatalog.Album(
                                        null,
                                        null,
                                        "favorites",
                                        "SYSTEM",
                                        "收藏",
                                        "",
                                        null,
                                        null,
                                        null,
                                        albums.countFavoriteMedia(spaceId, accountId),
                                        null))));
        Map<Long, List<AlbumCatalog.Album>> byGroup = new LinkedHashMap<>();
        List<AlbumRepository.GroupRow> groupRows = albums.findGroups(spaceId);
        for (AlbumRepository.GroupRow group : groupRows) {
            byGroup.put(group.internalId(), new ArrayList<>());
            groups.add(
                    new AlbumCatalog.Group(
                            group.id(), "CUSTOM", group.name(), byGroup.get(group.internalId())));
        }
        List<AlbumRepository.AlbumRow> albumRows = albums.findAlbums(spaceId);
        Map<UUID, MediaAsset> covers =
                media
                        .findByPublicIdsInSpace(
                                spaceId,
                                albumRows.stream()
                                        .map(AlbumRepository.AlbumRow::coverAssetId)
                                        .filter(java.util.Objects::nonNull)
                                        .distinct()
                                        .toList())
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(MediaAsset::id, value -> value));
        boolean hasUngrouped = false;
        for (AlbumRepository.AlbumRow album : albumRows) {
            List<AlbumCatalog.Album> target =
                    album.groupInternalId() == null ? null : byGroup.get(album.groupInternalId());
            if (target == null) {
                target = byGroup.computeIfAbsent(0L, ignored -> new ArrayList<>());
                if (!hasUngrouped) {
                    groups.add(new AlbumCatalog.Group(null, "CUSTOM", "未分组", target));
                    hasUngrouped = true;
                }
            }
            target.add(toAlbum(album, album.groupId(), covers.get(album.coverAssetId())));
        }
        return new AlbumCatalog(List.copyOf(groups));
    }

    public AlbumCatalog.Detail detail(
            UUID spaceId, UUID albumId, long accountId, int page, int size) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        PageBounds bounds = PageBounds.of(page, size);
        return cache.get(
                ReadCacheInvalidator.ALBUM_METADATA,
                spaceId,
                accountId,
                "detail:" + albumId + ":" + bounds.offset() + ":" + bounds.size(),
                Duration.ofMinutes(2),
                DETAIL,
                () -> cacheSafe(detail(space.internalId(), albumId, accountId, bounds)));
    }

    private AlbumCatalog.Detail detail(
            long spaceId, UUID albumId, long accountId, PageBounds bounds) {
        AlbumRepository.AlbumRow row =
                albums.findAlbum(spaceId, albumId)
                        .orElseThrow(() -> ApiException.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问"));
        List<UUID> ids =
                albums.findMediaPublicIds(
                        spaceId, albumId, accountId, bounds.offset(), bounds.size());
        List<MediaAsset> items = media.findByPublicIdsInSpace(spaceId, ids);
        MediaAsset cover =
                row.coverAssetId() == null
                        ? null
                        : media
                                .findByPublicIdsInSpace(spaceId, List.of(row.coverAssetId()))
                                .stream()
                                .findFirst()
                                .orElse(null);
        return new AlbumCatalog.Detail(
                toAlbum(row, row.groupId(), cover),
                items,
                albums.countMedia(spaceId, albumId, accountId));
    }

    public AlbumCatalog.Detail systemDetail(
            UUID spaceId, String key, long accountId, int page, int size) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        if (!"all".equals(key) && !"favorites".equals(key)) {
            throw ApiException.notFound("ALBUM_NOT_FOUND", "系统相册不存在");
        }
        PageBounds bounds = PageBounds.of(page, size);
        return cache.get(
                ReadCacheInvalidator.ALBUM_METADATA,
                spaceId,
                accountId,
                "system:" + key + ":" + bounds.offset() + ":" + bounds.size(),
                Duration.ofMinutes(2),
                DETAIL,
                () -> cacheSafe(systemDetail(space.internalId(), key, accountId, bounds)));
    }

    private AlbumCatalog.Detail systemDetail(
            long spaceId, String key, long accountId, PageBounds bounds) {
        List<UUID> ids =
                "favorites".equals(key)
                        ? albums.findFavoritePublicIds(
                                spaceId, accountId, bounds.offset(), bounds.size())
                        : albums.findLibraryPublicIds(
                                spaceId, accountId, bounds.offset(), bounds.size());
        List<MediaAsset> items = media.findByPublicIds(spaceId, ids, accountId);
        long total =
                "favorites".equals(key)
                        ? albums.countFavoriteMedia(spaceId, accountId)
                        : albums.countLibraryImages(spaceId, accountId);
        AlbumCatalog.Album album =
                new AlbumCatalog.Album(
                        null,
                        null,
                        key,
                        "SYSTEM",
                        "favorites".equals(key) ? "收藏" : "所有图片",
                        "",
                        null,
                        null,
                        null,
                        total,
                        null);
        return new AlbumCatalog.Detail(album, items, total);
    }

    private record PageBounds(int offset, int size) {
        static PageBounds of(int page, int size) {
            int normalizedPage = Math.max(0, page);
            int normalizedSize = Math.max(1, Math.min(size, 60));
            long offset = (long) normalizedPage * normalizedSize;
            return new PageBounds((int) Math.min(offset, Integer.MAX_VALUE), normalizedSize);
        }
    }

    @Transactional
    public AlbumCatalog.Group createGroup(UUID spaceId, long accountId, String name) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        String value = normalizeName(name);
        try {
            long id =
                    albums.insertGroup(
                            new AlbumRepository.NewGroup(
                                    UUID.randomUUID(), space.internalId(), value, 0, accountId));
            AlbumRepository.GroupRow row =
                    albums.findGroups(space.internalId()).stream()
                            .filter(group -> group.internalId() == id)
                            .findFirst()
                            .orElseThrow();
            AlbumCatalog.Group created =
                    new AlbumCatalog.Group(row.id(), "CUSTOM", row.name(), List.of());
            cacheInvalidator.albums(spaceId);
            return created;
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "ALBUM_GROUP_EXISTS",
                    "当前空间已存在同名相册组");
        }
    }

    @Transactional
    public AlbumCatalog.Group updateGroup(UUID spaceId, UUID groupId, long accountId, String name) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        String value = normalizeName(name);
        try {
            if (!albums.updateGroup(space.internalId(), groupId, value)) {
                throw ApiException.notFound("ALBUM_GROUP_NOT_FOUND", "相册组不存在或无权访问");
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "ALBUM_GROUP_EXISTS",
                    "当前空间已存在同名相册组");
        }
        AlbumCatalog.Group updated =
                albums.findGroups(space.internalId()).stream()
                        .filter(group -> group.id().equals(groupId))
                        .map(
                                group ->
                                        new AlbumCatalog.Group(
                                                group.id(), "CUSTOM", group.name(), List.of()))
                        .findFirst()
                        .orElseThrow();
        cacheInvalidator.albums(spaceId);
        return updated;
    }

    @Transactional
    public void deleteGroup(UUID spaceId, UUID groupId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        if (!albums.deleteGroup(space.internalId(), groupId)) {
            throw ApiException.badRequest("ALBUM_GROUP_NOT_EMPTY", "请先删除或移动相册组内的相册");
        }
        cacheInvalidator.albums(spaceId);
    }

    @Transactional
    public AlbumCatalog.Album createAlbum(
            UUID spaceId,
            long accountId,
            UUID groupId,
            String name,
            String description,
            List<UUID> mediaIds,
            boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        String value = normalizeName(name);
        Long groupInternalId = resolveGroup(space.internalId(), groupId);
        List<MediaAsset> assets =
                validateMedia(spaceId, space.internalId(), accountId, mediaIds, elevated);
        Long cover = assets.isEmpty() ? null : assets.get(0).internalId();
        UUID publicId = UUID.randomUUID();
        long albumId;
        try {
            albumId =
                    albums.insertAlbum(
                            new AlbumRepository.NewAlbum(
                                    publicId,
                                    space.internalId(),
                                    groupInternalId,
                                    accountId,
                                    value,
                                    description == null ? null : description.trim(),
                                    "CUSTOM",
                                    cover,
                                    0));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.CONFLICT, "ALBUM_EXISTS", "当前空间已存在同名相册");
        }
        for (int i = 0; i < assets.size(); i++)
            albums.insertMedia(space.internalId(), albumId, assets.get(i).internalId(), i);
        MediaAsset.Variant coverVariant = assets.isEmpty() ? null : coverVariant(assets.get(0));
        AlbumCatalog.Album created =
                new AlbumCatalog.Album(
                        publicId,
                        groupId,
                        null,
                        "CUSTOM",
                        value,
                        description,
                        assets.isEmpty() ? null : assets.get(0).id(),
                        coverVariant == null ? null : coverVariant.type(),
                        coverVariant == null ? null : coverVariant.profile(),
                        assets.size(),
                        assets.isEmpty() ? null : assets.get(0));
        cacheInvalidator.albums(spaceId);
        return created;
    }

    @Transactional
    public AlbumCatalog.Album createAiAlbum(
            UUID spaceId, long accountId, String name, String description, List<UUID> mediaIds) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        String value = normalizeName(name);
        List<MediaAsset> assets =
                validateMedia(spaceId, space.internalId(), accountId, mediaIds, false);
        AlbumRepository.GroupRow group = findOrCreateAiGroup(space.internalId(), accountId);
        UUID publicId = UUID.randomUUID();
        long albumId =
                albums.insertAlbum(
                        new AlbumRepository.NewAlbum(
                                publicId,
                                space.internalId(),
                                group.internalId(),
                                accountId,
                                value,
                                description == null || description.isBlank()
                                        ? null
                                        : description.trim(),
                                "AI",
                                assets.isEmpty() ? null : assets.get(0).internalId(),
                                0));
        for (int index = 0; index < assets.size(); index++) {
            albums.insertMedia(space.internalId(), albumId, assets.get(index).internalId(), index);
        }
        MediaAsset.Variant coverVariant = assets.isEmpty() ? null : coverVariant(assets.get(0));
        AlbumCatalog.Album created =
                new AlbumCatalog.Album(
                        publicId,
                        group.id(),
                        null,
                        "AI",
                        value,
                        description,
                        assets.isEmpty() ? null : assets.get(0).id(),
                        coverVariant == null ? null : coverVariant.type(),
                        coverVariant == null ? null : coverVariant.profile(),
                        assets.size(),
                        assets.isEmpty() ? null : assets.get(0));
        cacheInvalidator.albums(spaceId);
        return created;
    }

    private AlbumRepository.GroupRow findOrCreateAiGroup(long spaceId, long accountId) {
        AlbumRepository.GroupRow existing =
                albums.findGroups(spaceId).stream()
                        .filter(item -> "AI 整理".equals(item.name()))
                        .findFirst()
                        .orElse(null);
        if (existing != null) return existing;
        try {
            long id =
                    albums.insertGroup(
                            new AlbumRepository.NewGroup(
                                    UUID.randomUUID(), spaceId, "AI 整理", 10, accountId));
            return albums.findGroups(spaceId).stream()
                    .filter(item -> item.internalId() == id)
                    .findFirst()
                    .orElseThrow();
        } catch (DuplicateKeyException exception) {
            return albums.findGroups(spaceId).stream()
                    .filter(item -> "AI 整理".equals(item.name()))
                    .findFirst()
                    .orElseThrow(() -> exception);
        }
    }

    @Transactional
    public AlbumCatalog.Album updateAlbum(
            UUID spaceId,
            UUID albumId,
            long accountId,
            UUID groupId,
            String name,
            String description) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        String value = normalizeName(name);
        Long groupInternalId = resolveGroup(space.internalId(), groupId);
        String normalizedDescription =
                description == null || description.isBlank() ? null : description.trim();
        try {
            if (!albums.updateAlbum(
                    space.internalId(), albumId, groupInternalId, value, normalizedDescription)) {
                throw ApiException.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问");
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.CONFLICT, "ALBUM_EXISTS", "当前空间已存在同名相册");
        }
        AlbumRepository.AlbumRow row = albums.findAlbum(space.internalId(), albumId).orElseThrow();
        MediaAsset cover =
                row.coverAssetId() == null
                        ? null
                        : media
                                .findByPublicIdsInSpace(
                                        space.internalId(), List.of(row.coverAssetId()))
                                .stream()
                                .findFirst()
                                .orElse(null);
        AlbumCatalog.Album updated = toAlbum(row, row.groupId(), cover);
        cacheInvalidator.albums(spaceId);
        return updated;
    }

    @Transactional
    public void deleteAlbum(UUID spaceId, UUID albumId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        if (!albums.softDeleteAlbum(space.internalId(), albumId)) {
            throw ApiException.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问");
        }
        cacheInvalidator.albums(spaceId);
    }

    @Transactional
    public void addMedia(
            UUID spaceId, UUID albumId, long accountId, List<UUID> mediaIds, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        AlbumRepository.AlbumRow album =
                albums.findAlbum(space.internalId(), albumId)
                        .orElseThrow(() -> ApiException.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问"));
        List<MediaAsset> assets =
                validateMedia(spaceId, space.internalId(), accountId, mediaIds, elevated);
        List<UUID> current = albums.findMediaPublicIds(space.internalId(), albumId, accountId);
        int position = current.size();
        for (MediaAsset asset : assets) {
            if (!current.contains(asset.id()))
                albums.insertMedia(
                        space.internalId(), album.internalId(), asset.internalId(), position++);
        }
        if (album.coverAssetId() == null && !assets.isEmpty())
            albums.updateCover(space.internalId(), album.internalId(), assets.get(0).internalId());
        cacheInvalidator.albums(spaceId);
    }

    @Transactional
    public void removeMedia(
            UUID spaceId, UUID albumId, UUID assetId, long accountId, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        AlbumRepository.AlbumRow album =
                albums.findAlbum(space.internalId(), albumId)
                        .orElseThrow(() -> ApiException.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问"));
        MediaAsset asset =
                media.findByPublicIdsInSpace(space.internalId(), List.of(assetId)).stream()
                        .findFirst()
                        .orElseThrow(() -> ApiException.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权访问"));
        mediaAccess.require(
                spaceId, assetId, MediaAccessContext.album(accountId, albumId, elevated));
        albums.deleteMedia(space.internalId(), album.internalId(), asset.internalId());
        if (assetId.equals(album.coverAssetId())) {
            UUID replacement =
                    albums.findMediaPublicIds(space.internalId(), albumId, accountId, 0, 1).stream()
                            .findFirst()
                            .orElse(null);
            Long replacementId =
                    replacement == null
                            ? null
                            : media
                                    .findByPublicIds(
                                            space.internalId(), List.of(replacement), accountId)
                                    .stream()
                                    .map(MediaAsset::internalId)
                                    .findFirst()
                                    .orElse(null);
            albums.updateCover(space.internalId(), album.internalId(), replacementId);
        }
        cacheInvalidator.albums(spaceId);
    }

    @Transactional
    public void favorite(
            UUID spaceId, UUID assetId, long accountId, boolean value, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        MediaAsset asset =
                media.findByPublicIds(space.internalId(), List.of(assetId), accountId).stream()
                        .findFirst()
                        .orElseThrow(() -> ApiException.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权访问"));
        mediaAccess.require(spaceId, assetId, MediaAccessContext.direct(accountId, elevated));
        if (value) albums.addFavorite(space.internalId(), accountId, asset.internalId());
        else albums.removeFavorite(space.internalId(), accountId, asset.internalId());
        cacheInvalidator.albums(spaceId);
    }

    private Long resolveGroup(long spaceId, UUID groupId) {
        if (groupId == null) return null;
        return albums.findGroups(spaceId).stream()
                .filter(group -> group.id().equals(groupId))
                .map(AlbumRepository.GroupRow::internalId)
                .findFirst()
                .orElseThrow(
                        () -> ApiException.badRequest("ALBUM_GROUP_NOT_FOUND", "相册组不存在或不属于当前空间"));
    }

    private List<MediaAsset> validateMedia(
            UUID spacePublicId, long spaceId, long accountId, List<UUID> ids, boolean elevated) {
        List<UUID> values = ids == null ? List.of() : ids.stream().distinct().toList();
        List<MediaAsset> assets = media.findByPublicIds(spaceId, values, accountId);
        if (assets.size() != values.size())
            throw ApiException.badRequest("MEDIA_NOT_FOUND", "部分媒体不存在或不属于当前空间");
        assets.forEach(
                asset ->
                        mediaAccess.require(
                                spacePublicId,
                                asset.id(),
                                MediaAccessContext.direct(accountId, elevated)));
        return assets;
    }

    private String normalizeName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank()) throw ApiException.badRequest("ALBUM_NAME_REQUIRED", "相册名称不能为空");
        if (value.length() > 100)
            throw ApiException.badRequest("ALBUM_NAME_TOO_LONG", "相册名称不能超过100个字符");
        return value;
    }

    private AlbumCatalog.Album toAlbum(
            AlbumRepository.AlbumRow row, UUID groupId, MediaAsset cover) {
        return new AlbumCatalog.Album(
                row.id(),
                groupId,
                null,
                row.type(),
                row.name(),
                row.description(),
                row.coverAssetId(),
                row.coverVariantType(),
                row.coverVariantProfile(),
                row.mediaCount(),
                cover);
    }

    private MediaAsset.Variant coverVariant(MediaAsset asset) {
        return asset.variants().stream()
                .filter(value -> "READY".equals(value.status()))
                .filter(
                        value ->
                                "THUMBNAIL".equals(value.type()) || "ORIGINAL".equals(value.type()))
                .min(
                        java.util.Comparator.comparingInt(
                                        (MediaAsset.Variant value) ->
                                                "THUMBNAIL".equals(value.type()) ? 0 : 1)
                                .thenComparingInt(
                                        value ->
                                                "default".equals(value.profile())
                                                        ? 0
                                                        : "source".equals(value.profile()) ? 1 : 2)
                                .thenComparing(MediaAsset.Variant::profile))
                .orElse(null);
    }

    private AlbumCatalog cacheSafe(AlbumCatalog value) {
        return new AlbumCatalog(
                value.groups().stream()
                        .map(
                                group ->
                                        new AlbumCatalog.Group(
                                                group.id(),
                                                group.type(),
                                                group.name(),
                                                group.albums().stream()
                                                        .map(this::cacheSafe)
                                                        .toList()))
                        .toList());
    }

    private AlbumCatalog.Detail cacheSafe(AlbumCatalog.Detail value) {
        return new AlbumCatalog.Detail(
                cacheSafe(value.album()),
                value.media().stream().map(this::cacheSafe).toList(),
                value.totalMedia());
    }

    private AlbumCatalog.Album cacheSafe(AlbumCatalog.Album value) {
        return new AlbumCatalog.Album(
                value.id(),
                value.groupId(),
                value.systemKey(),
                value.type(),
                value.name(),
                value.description(),
                value.coverAssetId(),
                value.coverVariantType(),
                value.coverVariantProfile(),
                value.mediaCount(),
                value.coverMedia() == null ? null : cacheSafe(value.coverMedia()));
    }

    private MediaAsset cacheSafe(MediaAsset value) {
        return new MediaAsset(
                value.internalId(),
                value.id(),
                value.spaceId(),
                value.ownerId(),
                value.mediaType(),
                value.originalFilename(),
                value.caption(),
                value.takenAt(),
                value.accessScope(),
                value.libraryVisible(),
                value.status(),
                value.createdAt(),
                value.updatedAt(),
                value.variants().stream()
                        .map(
                                variant ->
                                        new MediaAsset.Variant(
                                                variant.type(),
                                                variant.profile(),
                                                null,
                                                null,
                                                variant.contentType(),
                                                variant.sizeBytes(),
                                                null,
                                                variant.width(),
                                                variant.height(),
                                                variant.durationMillis(),
                                                variant.status()))
                        .toList());
    }
}
