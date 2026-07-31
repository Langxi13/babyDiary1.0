package com.langxi.babydiary.album.application;

import com.langxi.babydiary.album.domain.AlbumCatalog;
import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.MediaAccessPolicy;
import com.langxi.babydiary.media.application.MediaRepository;
import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.ReadCacheInvalidator;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlbumService {
    private static final int MAX_MEDIA_PER_COMMAND = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 2_000;

    private final SpaceAccess spaces;
    private final AlbumRepository albums;
    private final MediaRepository media;
    private final MediaAccessPolicy mediaAccess;
    private final ReadCacheInvalidator cacheInvalidator;

    public AlbumService(
            SpaceAccess spaces,
            AlbumRepository albums,
            MediaRepository media,
            MediaAccessPolicy mediaAccess,
            ReadCacheInvalidator cacheInvalidator) {
        this.spaces = spaces;
        this.albums = albums;
        this.media = media;
        this.mediaAccess = mediaAccess;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Transactional
    public AlbumCatalog.Group createGroup(UUID spaceId, long accountId, String name) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        String value = normalizeName(name);
        UUID publicId = UUID.randomUUID();
        try {
            albums.insertGroup(
                    new AlbumRepository.NewGroup(
                            publicId, space.internalId(), value, 0, accountId));
            AlbumCatalog.Group created =
                    new AlbumCatalog.Group(publicId, "CUSTOM", value, List.of());
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
        AlbumCatalog.Group updated = new AlbumCatalog.Group(groupId, "CUSTOM", value, List.of());
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
        String normalizedDescription = normalizeDescription(description);
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
                                    normalizedDescription,
                                    "CUSTOM",
                                    cover,
                                    0));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.CONFLICT, "ALBUM_EXISTS", "当前空间已存在同名相册");
        }
        for (int i = 0; i < assets.size(); i++)
            albums.insertMedia(space.internalId(), albumId, assets.get(i).internalId(), i);
        MediaAsset.Variant coverVariant =
                assets.isEmpty() ? null : AlbumProjection.coverVariant(assets.get(0));
        AlbumCatalog.Album created =
                new AlbumCatalog.Album(
                        publicId,
                        groupId,
                        null,
                        "CUSTOM",
                        value,
                        normalizedDescription,
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
        String normalizedDescription = normalizeDescription(description);
        long albumId;
        try {
            albumId =
                    albums.insertAlbum(
                            new AlbumRepository.NewAlbum(
                                    publicId,
                                    space.internalId(),
                                    group.internalId(),
                                    accountId,
                                    value,
                                    normalizedDescription,
                                    "AI",
                                    assets.isEmpty() ? null : assets.get(0).internalId(),
                                    0));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.CONFLICT, "ALBUM_EXISTS", "当前空间已存在同名相册");
        }
        for (int index = 0; index < assets.size(); index++) {
            albums.insertMedia(space.internalId(), albumId, assets.get(index).internalId(), index);
        }
        MediaAsset.Variant coverVariant =
                assets.isEmpty() ? null : AlbumProjection.coverVariant(assets.get(0));
        AlbumCatalog.Album created =
                new AlbumCatalog.Album(
                        publicId,
                        group.id(),
                        null,
                        "AI",
                        value,
                        normalizedDescription,
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
        UUID publicId = UUID.randomUUID();
        try {
            long id =
                    albums.insertGroup(
                            new AlbumRepository.NewGroup(
                                    publicId, spaceId, "AI 整理", 10, accountId));
            return new AlbumRepository.GroupRow(id, publicId, "AI 整理", 10);
        } catch (DuplicateKeyException exception) {
            return albums.findGroups(spaceId).stream()
                    .filter(item -> "AI 整理".equals(item.name()))
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "Concurrent AI album group insert was not visible",
                                            exception));
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
        String normalizedDescription = normalizeDescription(description);
        try {
            if (!albums.updateAlbum(
                    space.internalId(), albumId, groupInternalId, value, normalizedDescription)) {
                throw ApiException.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问");
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.CONFLICT, "ALBUM_EXISTS", "当前空间已存在同名相册");
        }
        AlbumRepository.AlbumRow row =
                albums.findAlbum(space.internalId(), albumId, true)
                        .orElseThrow(
                                () -> ApiException.notFound("ALBUM_NOT_FOUND", "相册已被并发删除，请刷新后重试"));
        MediaAsset cover =
                row.coverAssetId() == null
                        ? null
                        : media
                                .findByPublicIdsInSpace(
                                        space.internalId(), List.of(row.coverAssetId()))
                                .stream()
                                .findFirst()
                                .orElse(null);
        AlbumCatalog.Album updated = AlbumProjection.album(row, row.groupId(), cover);
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
                albums.findAlbum(space.internalId(), albumId, true)
                        .orElseThrow(() -> ApiException.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问"));
        List<MediaAsset> assets =
                validateMedia(spaceId, space.internalId(), accountId, mediaIds, elevated);
        List<UUID> current = albums.findMediaPublicIds(space.internalId(), albumId);
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
                albums.findAlbum(space.internalId(), albumId, true)
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
                    albums.findMediaPublicIds(space.internalId(), albumId, true, 0, 1).stream()
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
        if (values.size() > MAX_MEDIA_PER_COMMAND) {
            throw ApiException.badRequest("ALBUM_MEDIA_LIMIT", "单次最多选择200个媒体");
        }
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

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) return null;
        String value = description.trim();
        if (value.length() > MAX_DESCRIPTION_LENGTH) {
            throw ApiException.badRequest("ALBUM_DESCRIPTION_TOO_LONG", "相册描述不能超过2000个字符");
        }
        return value;
    }
}
