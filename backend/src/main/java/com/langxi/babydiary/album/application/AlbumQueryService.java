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
        List<AlbumCatalog.Group> groups = new ArrayList<>();
        List<AlbumRepository.YearBucket> years =
                albums.findLibraryYears(spaceId, accountId, elevated);
        Map<UUID, MediaAsset> yearCovers =
                media
                        .findByPublicIdsInSpace(
                                spaceId,
                                years.stream()
                                        .map(AlbumRepository.YearBucket::coverAssetId)
                                        .toList())
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(MediaAsset::id, value -> value));
        List<AlbumCatalog.Album> systemAlbums = new ArrayList<>();
        systemAlbums.add(
                systemAlbum(
                        "all", "所有图片", albums.countLibraryImages(spaceId, accountId, elevated)));
        systemAlbums.add(
                systemAlbum(
                        "favorites",
                        "收藏",
                        albums.countFavoriteMedia(spaceId, accountId, elevated)));
        for (AlbumRepository.YearBucket year : years) {
            MediaAsset cover = yearCovers.get(year.coverAssetId());
            MediaAsset.Variant coverVariant =
                    cover == null ? null : AlbumProjection.coverVariant(cover);
            systemAlbums.add(
                    new AlbumCatalog.Album(
                            null,
                            null,
                            "year:" + year.year(),
                            "SYSTEM",
                            year.year() + " 年",
                            "",
                            year.coverAssetId(),
                            coverVariant == null ? null : coverVariant.type(),
                            coverVariant == null ? null : coverVariant.profile(),
                            year.mediaCount(),
                            cover));
        }
        groups.add(new AlbumCatalog.Group(null, "SYSTEM", "默认相册", List.copyOf(systemAlbums)));

        Map<Long, List<AlbumCatalog.Album>> byGroup = new LinkedHashMap<>();
        List<AlbumRepository.GroupRow> groupRows = albums.findGroups(spaceId);
        for (AlbumRepository.GroupRow group : groupRows) {
            byGroup.put(group.internalId(), new ArrayList<>());
            groups.add(
                    new AlbumCatalog.Group(
                            group.id(), "CUSTOM", group.name(), byGroup.get(group.internalId())));
        }
        List<AlbumRepository.AlbumRow> albumRows = albums.findAlbums(spaceId, elevated);
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
            target.add(
                    AlbumProjection.album(
                            album, album.groupId(), covers.get(album.coverAssetId())));
        }
        return new AlbumCatalog(List.copyOf(groups));
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
                AlbumProjection.album(row, row.groupId(), cover),
                items,
                albums.countMedia(spaceId, albumId, elevated));
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
        List<MediaAsset> items = media.findByPublicIds(spaceId, ids, accountId);
        long total =
                year != null
                        ? albums.countLibraryImagesByYear(spaceId, accountId, year, elevated)
                        : "favorites".equals(key)
                                ? albums.countFavoriteMedia(spaceId, accountId, elevated)
                                : albums.countLibraryImages(spaceId, accountId, elevated);
        String name = year != null ? year + " 年" : "favorites".equals(key) ? "收藏" : "所有图片";
        return new AlbumCatalog.Detail(systemAlbum(key, name, total), items, total);
    }

    private AlbumCatalog.Album systemAlbum(String key, String name, long count) {
        return new AlbumCatalog.Album(
                null, null, key, "SYSTEM", name, "", null, null, null, count, null);
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
