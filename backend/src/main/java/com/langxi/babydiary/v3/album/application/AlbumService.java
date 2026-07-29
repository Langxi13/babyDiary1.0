package com.langxi.babydiary.v3.album.application;

import com.langxi.babydiary.v3.album.domain.AlbumCatalog;
import com.langxi.babydiary.v3.media.application.MediaRepository;
import com.langxi.babydiary.v3.media.domain.MediaAsset;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.space.application.SpaceAccess;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AlbumService {
    private final SpaceAccess spaces;
    private final AlbumRepository albums;
    private final MediaRepository media;

    public AlbumService(SpaceAccess spaces, AlbumRepository albums, MediaRepository media) {
        this.spaces = spaces;
        this.albums = albums;
        this.media = media;
    }

    public AlbumCatalog catalog(UUID spaceId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        List<AlbumCatalog.Group> groups = new ArrayList<>();
        groups.add(new AlbumCatalog.Group(null, "SYSTEM", "默认相册", List.of(
                new AlbumCatalog.Album(null, null, "all", "SYSTEM", "所有图片", "", null,
                        albums.countLibraryImages(space.internalId(), accountId)),
                new AlbumCatalog.Album(null, null, "favorites", "SYSTEM", "收藏", "", null,
                        albums.countFavoriteMedia(space.internalId(), accountId))
        )));
        Map<Long, List<AlbumCatalog.Album>> byGroup = new LinkedHashMap<>();
        for (AlbumRepository.GroupRow group : albums.findGroups(space.internalId())) {
            byGroup.put(group.internalId(), new ArrayList<>());
            groups.add(new AlbumCatalog.Group(group.id(), "CUSTOM", group.name(), byGroup.get(group.internalId())));
        }
        Map<Long, UUID> groupPublicIds = new LinkedHashMap<>();
        albums.findGroups(space.internalId()).forEach(group -> groupPublicIds.put(group.internalId(), group.id()));
        boolean hasUngrouped = false;
        for (AlbumRepository.AlbumRow album : albums.findAlbums(space.internalId())) {
            List<AlbumCatalog.Album> target = album.groupInternalId() == null ? null : byGroup.get(album.groupInternalId());
            if (target == null) {
                target = byGroup.computeIfAbsent(0L, ignored -> new ArrayList<>());
                if (!hasUngrouped) {
                    groups.add(new AlbumCatalog.Group(null, "CUSTOM", "未分组", target));
                    hasUngrouped = true;
                }
            }
            target.add(toAlbum(album, album.groupId()));
        }
        return new AlbumCatalog(List.copyOf(groups));
    }

    public AlbumCatalog.Detail detail(UUID spaceId, UUID albumId, long accountId, int page, int size) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        AlbumRepository.AlbumRow row = albums.findAlbum(space.internalId(), albumId)
                .orElseThrow(() -> V3Exception.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问"));
        PageBounds bounds = PageBounds.of(page, size);
        List<UUID> ids = albums.findMediaPublicIds(space.internalId(), albumId, accountId, bounds.offset(), bounds.size());
        return new AlbumCatalog.Detail(toAlbum(row, row.groupId()),
                media.findByPublicIds(space.internalId(), ids, accountId),
                albums.countMedia(space.internalId(), albumId, accountId));
    }

    public AlbumCatalog.Detail systemDetail(UUID spaceId, String key, long accountId, int page, int size) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        if (!"all".equals(key) && !"favorites".equals(key)) {
            throw V3Exception.notFound("ALBUM_NOT_FOUND", "系统相册不存在");
        }
        PageBounds bounds = PageBounds.of(page, size);
        List<UUID> ids = "favorites".equals(key)
                ? albums.findFavoritePublicIds(space.internalId(), accountId, bounds.offset(), bounds.size())
                : albums.findLibraryPublicIds(space.internalId(), accountId, bounds.offset(), bounds.size());
        List<MediaAsset> items = media.findByPublicIds(space.internalId(), ids, accountId);
        long total = "favorites".equals(key)
                ? albums.countFavoriteMedia(space.internalId(), accountId)
                : albums.countLibraryImages(space.internalId(), accountId);
        AlbumCatalog.Album album = new AlbumCatalog.Album(null, null, key, "SYSTEM",
                "favorites".equals(key) ? "收藏" : "所有图片", "", null, total);
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
            long id = albums.insertGroup(new AlbumRepository.NewGroup(UUID.randomUUID(), space.internalId(), value, 0, accountId));
            AlbumRepository.GroupRow row = albums.findGroups(space.internalId()).stream()
                    .filter(group -> group.internalId() == id).findFirst().orElseThrow();
            return new AlbumCatalog.Group(row.id(), "CUSTOM", row.name(), List.of());
        } catch (DuplicateKeyException exception) {
            throw new V3Exception(org.springframework.http.HttpStatus.CONFLICT, "ALBUM_GROUP_EXISTS", "当前空间已存在同名相册组");
        }
    }

    @Transactional
    public AlbumCatalog.Group updateGroup(UUID spaceId, UUID groupId, long accountId, String name) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        String value = normalizeName(name);
        try {
            if (!albums.updateGroup(space.internalId(), groupId, value)) {
                throw V3Exception.notFound("ALBUM_GROUP_NOT_FOUND", "相册组不存在或无权访问");
            }
        } catch (DuplicateKeyException exception) {
            throw new V3Exception(org.springframework.http.HttpStatus.CONFLICT, "ALBUM_GROUP_EXISTS", "当前空间已存在同名相册组");
        }
        return albums.findGroups(space.internalId()).stream().filter(group -> group.id().equals(groupId))
                .map(group -> new AlbumCatalog.Group(group.id(), "CUSTOM", group.name(), List.of())).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteGroup(UUID spaceId, UUID groupId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        if (!albums.deleteGroup(space.internalId(), groupId)) {
            throw V3Exception.badRequest("ALBUM_GROUP_NOT_EMPTY", "请先删除或移动相册组内的相册");
        }
    }

    @Transactional
    public AlbumCatalog.Album createAlbum(UUID spaceId, long accountId, UUID groupId, String name,
                                          String description, List<UUID> mediaIds) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        String value = normalizeName(name);
        Long groupInternalId = resolveGroup(space.internalId(), groupId);
        List<MediaAsset> assets = validateMedia(space.internalId(), accountId, mediaIds);
        Long cover = assets.isEmpty() ? null : assets.get(0).internalId();
        UUID publicId = UUID.randomUUID();
        long albumId;
        try {
            albumId = albums.insertAlbum(new AlbumRepository.NewAlbum(publicId, space.internalId(), groupInternalId,
                    accountId, value, description == null ? null : description.trim(), "CUSTOM", cover, 0));
        } catch (DuplicateKeyException exception) {
            throw new V3Exception(org.springframework.http.HttpStatus.CONFLICT, "ALBUM_EXISTS", "当前空间已存在同名相册");
        }
        for (int i = 0; i < assets.size(); i++) albums.insertMedia(space.internalId(), albumId, assets.get(i).internalId(), i);
        return new AlbumCatalog.Album(publicId, groupId, null, "CUSTOM", value, description, assets.isEmpty() ? null : assets.get(0).id(), assets.size());
    }

    @Transactional
    public AlbumCatalog.Album createAiAlbum(UUID spaceId, long accountId, String name,
                                            String description, List<UUID> mediaIds) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        String value = normalizeName(name);
        List<MediaAsset> assets = validateMedia(space.internalId(), accountId, mediaIds);
        AlbumRepository.GroupRow group = findOrCreateAiGroup(space.internalId(), accountId);
        UUID publicId = UUID.randomUUID();
        long albumId = albums.insertAlbum(new AlbumRepository.NewAlbum(publicId, space.internalId(), group.internalId(),
                accountId, value, description == null || description.isBlank() ? null : description.trim(), "AI",
                assets.isEmpty() ? null : assets.get(0).internalId(), 0));
        for (int index = 0; index < assets.size(); index++) {
            albums.insertMedia(space.internalId(), albumId, assets.get(index).internalId(), index);
        }
        return new AlbumCatalog.Album(publicId, group.id(), null, "AI", value, description,
                assets.isEmpty() ? null : assets.get(0).id(), assets.size());
    }

    private AlbumRepository.GroupRow findOrCreateAiGroup(long spaceId, long accountId) {
        AlbumRepository.GroupRow existing = albums.findGroups(spaceId).stream()
                .filter(item -> "AI 整理".equals(item.name())).findFirst().orElse(null);
        if (existing != null) return existing;
        try {
            long id = albums.insertGroup(new AlbumRepository.NewGroup(
                    UUID.randomUUID(), spaceId, "AI 整理", 10, accountId));
            return albums.findGroups(spaceId).stream()
                    .filter(item -> item.internalId() == id).findFirst().orElseThrow();
        } catch (DuplicateKeyException exception) {
            return albums.findGroups(spaceId).stream()
                    .filter(item -> "AI 整理".equals(item.name())).findFirst()
                    .orElseThrow(() -> exception);
        }
    }

    @Transactional
    public AlbumCatalog.Album updateAlbum(UUID spaceId, UUID albumId, long accountId, UUID groupId,
                                          String name, String description) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        String value = normalizeName(name);
        Long groupInternalId = resolveGroup(space.internalId(), groupId);
        String normalizedDescription = description == null || description.isBlank() ? null : description.trim();
        try {
            if (!albums.updateAlbum(space.internalId(), albumId, groupInternalId, value, normalizedDescription)) {
                throw V3Exception.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问");
            }
        } catch (DuplicateKeyException exception) {
            throw new V3Exception(org.springframework.http.HttpStatus.CONFLICT, "ALBUM_EXISTS", "当前空间已存在同名相册");
        }
        AlbumRepository.AlbumRow row = albums.findAlbum(space.internalId(), albumId).orElseThrow();
        return toAlbum(row, row.groupId());
    }

    @Transactional
    public void deleteAlbum(UUID spaceId, UUID albumId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        if (!albums.softDeleteAlbum(space.internalId(), albumId)) {
            throw V3Exception.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问");
        }
    }

    @Transactional
    public void addMedia(UUID spaceId, UUID albumId, long accountId, List<UUID> mediaIds) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        AlbumRepository.AlbumRow album = albums.findAlbum(space.internalId(), albumId)
                .orElseThrow(() -> V3Exception.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问"));
        List<MediaAsset> assets = validateMedia(space.internalId(), accountId, mediaIds);
        List<UUID> current = albums.findMediaPublicIds(space.internalId(), albumId, accountId);
        int position = current.size();
        for (MediaAsset asset : assets) {
            if (!current.contains(asset.id())) albums.insertMedia(space.internalId(), album.internalId(), asset.internalId(), position++);
        }
        if (album.coverAssetId() == null && !assets.isEmpty()) albums.updateCover(space.internalId(), album.internalId(), assets.get(0).internalId());
    }

    @Transactional
    public void removeMedia(UUID spaceId, UUID albumId, UUID assetId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        AlbumRepository.AlbumRow album = albums.findAlbum(space.internalId(), albumId)
                .orElseThrow(() -> V3Exception.notFound("ALBUM_NOT_FOUND", "相册不存在或无权访问"));
        MediaAsset asset = media.findByPublicIds(space.internalId(), List.of(assetId), accountId).stream().findFirst()
                .orElseThrow(() -> V3Exception.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权访问"));
        albums.deleteMedia(space.internalId(), album.internalId(), asset.internalId());
        if (assetId.equals(album.coverAssetId())) {
            UUID replacement = albums.findMediaPublicIds(space.internalId(), albumId, accountId, 0, 1)
                    .stream().findFirst().orElse(null);
            Long replacementId = replacement == null ? null : media.findByPublicIds(space.internalId(), List.of(replacement), accountId)
                    .stream().map(MediaAsset::internalId).findFirst().orElse(null);
            albums.updateCover(space.internalId(), album.internalId(), replacementId);
        }
    }

    @Transactional
    public void favorite(UUID spaceId, UUID assetId, long accountId, boolean value) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        MediaAsset asset = media.findByPublicIds(space.internalId(), List.of(assetId), accountId).stream().findFirst()
                .orElseThrow(() -> V3Exception.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权访问"));
        if (value) albums.addFavorite(space.internalId(), accountId, asset.internalId());
        else albums.removeFavorite(space.internalId(), accountId, asset.internalId());
    }

    private Long resolveGroup(long spaceId, UUID groupId) {
        if (groupId == null) return null;
        return albums.findGroups(spaceId).stream().filter(group -> group.id().equals(groupId))
                .map(AlbumRepository.GroupRow::internalId).findFirst()
                .orElseThrow(() -> V3Exception.badRequest("ALBUM_GROUP_NOT_FOUND", "相册组不存在或不属于当前空间"));
    }

    private List<MediaAsset> validateMedia(long spaceId, long accountId, List<UUID> ids) {
        List<UUID> values = ids == null ? List.of() : ids.stream().distinct().toList();
        List<MediaAsset> assets = media.findByPublicIds(spaceId, values, accountId);
        if (assets.size() != values.size()) throw V3Exception.badRequest("MEDIA_NOT_FOUND", "部分媒体不存在或不属于当前空间");
        return assets;
    }

    private String normalizeName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank()) throw V3Exception.badRequest("ALBUM_NAME_REQUIRED", "相册名称不能为空");
        if (value.length() > 100) throw V3Exception.badRequest("ALBUM_NAME_TOO_LONG", "相册名称不能超过100个字符");
        return value;
    }

    private AlbumCatalog.Album toAlbum(AlbumRepository.AlbumRow row, UUID groupId) {
        return new AlbumCatalog.Album(row.id(), groupId, null, row.type(), row.name(), row.description(),
                row.coverAssetId(), row.mediaCount());
    }
}
