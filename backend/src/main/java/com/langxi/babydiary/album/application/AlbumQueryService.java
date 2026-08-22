package com.langxi.babydiary.album.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.langxi.babydiary.album.domain.AlbumCatalog;
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
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AlbumQueryService {
    private static final TypeReference<AlbumCatalog> CATALOG = new TypeReference<>() {};
    private static final TypeReference<AlbumCatalog.Detail> DETAIL = new TypeReference<>() {};

    private final SpaceAccess spaces;
    private final AlbumRepository albums;
    private final MediaRepository media;
    private final ReadCache cache;

    public AlbumQueryService(
            SpaceAccess spaces, AlbumRepository albums, MediaRepository media, ReadCache cache) {
        this.spaces = spaces;
        this.albums = albums;
        this.media = media;
        this.cache = cache;
    }

    public AlbumCatalog catalog(UUID spaceId, long accountId, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        if (elevated) return catalog(space.internalId(), accountId, true);
        return cache.get(
                ReadCacheInvalidator.ALBUM_METADATA,
                spaceId,
                accountId,
                "catalog",
                Duration.ofMinutes(2),
                CATALOG,
                () -> cacheSafe(catalog(space.internalId(), accountId, false)));
    }

    private AlbumCatalog catalog(long spaceId, long accountId, boolean elevated) {
        List<AlbumRepository.SystemCatalogRow> systemRows =
                albums.findSystemCatalog(spaceId, accountId, elevated);
        List<AlbumRepository.CustomCatalogRow> customRows =
                albums.findCustomCatalog(spaceId, elevated);
        List<UUID> coverIds =
                java.util.stream.Stream.concat(
                                systemRows.stream()
                                        .map(AlbumRepository.SystemCatalogRow::coverAssetId),
                                customRows.stream()
                                        .map(AlbumRepository.CustomCatalogRow::coverAssetId))
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();
        Map<UUID, MediaAsset> covers =
                media.findByPublicIdsInSpace(spaceId, coverIds).stream()
                        .collect(java.util.stream.Collectors.toMap(MediaAsset::id, value -> value));

        List<AlbumCatalog.Group> groups = new ArrayList<>();
        List<AlbumCatalog.Album> systemAlbums =
                systemRows.stream()
                        .map(
                                row ->
                                        systemAlbum(
                                                row.systemKey(),
                                                systemAlbumName(row.systemKey()),
                                                row.mediaCount(),
                                                covers.get(row.coverAssetId())))
                        .toList();
        groups.add(new AlbumCatalog.Group(null, "SYSTEM", "默认相册", systemAlbums));

        Map<Long, List<AlbumCatalog.Album>> byGroup = new LinkedHashMap<>();
        Set<Long> addedGroups = new java.util.LinkedHashSet<>();
        for (AlbumRepository.CustomCatalogRow row : customRows) {
            long groupKey = row.groupInternalId() == null ? 0L : row.groupInternalId();
            List<AlbumCatalog.Album> target =
                    byGroup.computeIfAbsent(groupKey, ignored -> new ArrayList<>());
            if (addedGroups.add(groupKey)) {
                groups.add(
                        new AlbumCatalog.Group(
                                row.groupId(),
                                "CUSTOM",
                                row.groupInternalId() == null ? "未分组" : row.groupName(),
                                target));
            }
            if (row.albumId() == null) continue;
            AlbumRepository.AlbumRow album =
                    new AlbumRepository.AlbumRow(
                            row.albumInternalId(),
                            row.albumId(),
                            row.groupInternalId(),
                            row.groupId(),
                            row.albumType(),
                            row.albumName(),
                            row.albumDescription(),
                            row.coverAssetId(),
                            null,
                            null,
                            row.mediaCount());
            target.add(
                    AlbumProjection.album(
                            album,
                            row.groupId(),
                            row.coverAssetId(),
                            covers.get(row.coverAssetId())));
        }
        return new AlbumCatalog(List.copyOf(groups));
    }

    private String systemAlbumName(String key) {
        if ("all".equals(key)) return "所有图片";
        if ("favorites".equals(key)) return "收藏";
        return key.substring("year:".length()) + " 年";
    }

    public AlbumCatalog.Detail detail(
            UUID spaceId, UUID albumId, long accountId, int page, int size, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        PageBounds bounds = PageBounds.of(page, size);
        if (elevated) return detail(space.internalId(), albumId, bounds, true);
        return cache.get(
                ReadCacheInvalidator.ALBUM_METADATA,
                spaceId,
                accountId,
                "detail:" + albumId + ":" + bounds.offset() + ":" + bounds.size(),
                Duration.ofMinutes(2),
                DETAIL,
                () -> cacheSafe(detail(space.internalId(), albumId, bounds, false)));
    }

    private AlbumCatalog.Detail detail(
            long spaceId, UUID albumId, PageBounds bounds, boolean elevated) {
        AlbumRepository.AlbumRow row =
                albums.findAlbum(spaceId, albumId, elevated)
                        .orElseThrow(() -> ApiException.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问"));
        List<UUID> ids =
                albums.findMediaPublicIds(
                        spaceId, albumId, elevated, bounds.offset(), bounds.size());
        UUID coverId =
                row.coverAssetId() == null
                        ? ids.stream().findFirst().orElse(null)
                        : row.coverAssetId();
        List<UUID> hydrationIds = new ArrayList<>(ids);
        if (coverId != null && !hydrationIds.contains(coverId)) hydrationIds.add(coverId);
        Map<UUID, MediaAsset> hydrated =
                media.findByPublicIdsInSpace(spaceId, hydrationIds).stream()
                        .collect(java.util.stream.Collectors.toMap(MediaAsset::id, value -> value));
        List<MediaAsset> items =
                ids.stream().map(hydrated::get).filter(java.util.Objects::nonNull).toList();
        MediaAsset cover = hydrated.get(coverId);
        return new AlbumCatalog.Detail(
                AlbumProjection.album(row, row.groupId(), coverId, cover), items, row.mediaCount());
    }

    public AlbumCatalog.Detail systemDetail(
            UUID spaceId, String key, long accountId, int page, int size, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        Integer year = systemYear(key);
        if (!"all".equals(key) && !"favorites".equals(key) && year == null) {
            throw ApiException.notFound("ALBUM_NOT_FOUND", "系统相册不存在");
        }
        PageBounds bounds = PageBounds.of(page, size);
        if (elevated) {
            return systemDetail(space.internalId(), key, year, accountId, bounds, true);
        }
        return cache.get(
                ReadCacheInvalidator.ALBUM_METADATA,
                spaceId,
                accountId,
                "system:" + key + ":" + bounds.offset() + ":" + bounds.size(),
                Duration.ofMinutes(2),
                DETAIL,
                () ->
                        cacheSafe(
                                systemDetail(
                                        space.internalId(), key, year, accountId, bounds, false)));
    }

    private AlbumCatalog.Detail systemDetail(
            long spaceId,
            String key,
            Integer year,
            long accountId,
            PageBounds bounds,
            boolean elevated) {
        List<UUID> ids =
                year != null
                        ? albums.findLibraryPublicIdsByYear(
                                spaceId, accountId, year, elevated, bounds.offset(), bounds.size())
                        : "favorites".equals(key)
                                ? albums.findFavoritePublicIds(
                                        spaceId,
                                        accountId,
                                        elevated,
                                        bounds.offset(),
                                        bounds.size())
                                : albums.findLibraryPublicIds(
                                        spaceId,
                                        accountId,
                                        elevated,
                                        bounds.offset(),
                                        bounds.size());
        long total =
                year != null
                        ? albums.countLibraryImagesByYear(spaceId, accountId, year, elevated)
                        : "favorites".equals(key)
                                ? albums.countFavoriteMedia(spaceId, accountId, elevated)
                                : albums.countLibraryImages(spaceId, accountId, elevated);
        String name = year != null ? year + " 年" : "favorites".equals(key) ? "收藏" : "所有图片";
        List<MediaAsset> items = media.findByPublicIds(spaceId, ids, accountId);
        MediaAsset cover = items.stream().findFirst().orElse(null);
        return new AlbumCatalog.Detail(systemAlbum(key, name, total, cover), items, total);
    }

    private AlbumCatalog.Album systemAlbum(String key, String name, long count, MediaAsset cover) {
        MediaAsset.Variant coverVariant =
                cover == null ? null : AlbumProjection.coverVariant(cover);
        return new AlbumCatalog.Album(
                null,
                null,
                key,
                "SYSTEM",
                name,
                "",
                cover == null ? null : cover.id(),
                coverVariant == null ? null : coverVariant.type(),
                coverVariant == null ? null : coverVariant.profile(),
                count,
                cover);
    }

    private MediaAsset firstMedia(long spaceId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return media.findByPublicIdsInSpace(spaceId, List.of(ids.get(0))).stream()
                .findFirst()
                .orElse(null);
    }

    private Integer systemYear(String key) {
        if (key == null || !key.matches("year:[0-9]{4}")) return null;
        int year = Integer.parseInt(key.substring(5));
        return year >= 1900 && year <= 9999 ? year : null;
    }

    private record PageBounds(int offset, int size) {
        static PageBounds of(int page, int size) {
            int normalizedPage = Math.max(0, page);
            int normalizedSize = Math.max(1, Math.min(size, 60));
            long offset = (long) normalizedPage * normalizedSize;
            return new PageBounds((int) Math.min(offset, Integer.MAX_VALUE), normalizedSize);
        }
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
                value.derivativeVersion(),
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
                                                variant.qualityScore(),
                                                variant.status()))
                        .toList());
    }
}
