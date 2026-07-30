package com.langxi.babydiary.album.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlbumRepository {
    List<GroupRow> findGroups(long spaceId);

    List<AlbumRow> findAlbums(long spaceId);

    Optional<AlbumRow> findAlbum(long spaceId, UUID albumId);

    List<UUID> findMediaPublicIds(
            long spaceId, UUID albumId, long accountId, int offset, int limit);

    default List<UUID> findMediaPublicIds(long spaceId, UUID albumId, long accountId) {
        return findMediaPublicIds(spaceId, albumId, accountId, 0, Integer.MAX_VALUE);
    }

    long countMedia(long spaceId, UUID albumId, long accountId);

    long insertGroup(NewGroup group);

    boolean updateGroup(long spaceId, UUID groupId, String name);

    boolean deleteGroup(long spaceId, UUID groupId);

    long insertAlbum(NewAlbum album);

    boolean updateAlbum(long spaceId, UUID albumId, Long groupId, String name, String description);

    boolean softDeleteAlbum(long spaceId, UUID albumId);

    void insertMedia(long spaceId, long albumId, long assetId, int position);

    void deleteMedia(long spaceId, long albumId, long assetId);

    void deleteAllMedia(long spaceId, long albumId);

    void updateCover(long spaceId, long albumId, Long assetId);

    void addFavorite(long spaceId, long accountId, long assetId);

    void removeFavorite(long spaceId, long accountId, long assetId);

    List<UUID> findFavoritePublicIds(long spaceId, long accountId, int offset, int limit);

    long countFavoriteMedia(long spaceId, long accountId);

    List<UUID> findLibraryPublicIds(long spaceId, long accountId, int offset, int limit);

    long countLibraryImages(long spaceId, long accountId);

    record NewGroup(UUID publicId, long spaceId, String name, int sortOrder, long createdBy) {}

    record NewAlbum(
            UUID publicId,
            long spaceId,
            Long groupId,
            long createdBy,
            String name,
            String description,
            String type,
            Long coverAssetId,
            int sortOrder) {}

    record GroupRow(long internalId, UUID id, String name, int sortOrder) {}

    record AlbumRow(
            long internalId,
            UUID id,
            Long groupInternalId,
            UUID groupId,
            String type,
            String name,
            String description,
            UUID coverAssetId,
            String coverVariantType,
            String coverVariantProfile,
            long mediaCount) {}
}
