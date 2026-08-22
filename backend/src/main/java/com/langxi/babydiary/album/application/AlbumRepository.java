package com.langxi.babydiary.album.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlbumRepository {
    List<SystemCatalogRow> findSystemCatalog(
            long spaceId, long accountId, boolean includeProtected);

    List<CustomCatalogRow> findCustomCatalog(long spaceId, boolean includeProtected);

    List<GroupRow> findGroups(long spaceId);

    List<AlbumRow> findAlbums(long spaceId, boolean includeProtected);

    List<AlbumCover> findFallbackCovers(long spaceId, boolean includeProtected);

    Optional<AlbumRow> findAlbum(long spaceId, UUID albumId, boolean includeProtected);

    List<UUID> findMediaPublicIds(
            long spaceId, UUID albumId, boolean includeProtected, int offset, int limit);

    default List<UUID> findMediaPublicIds(long spaceId, UUID albumId) {
        return findMediaPublicIds(spaceId, albumId, true, 0, Integer.MAX_VALUE);
    }

    long countMedia(long spaceId, UUID albumId, boolean includeProtected);

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

    List<UUID> findFavoritePublicIds(
            long spaceId, long accountId, boolean includeProtected, int offset, int limit);

    long countFavoriteMedia(long spaceId, long accountId, boolean includeProtected);

    List<UUID> findLibraryPublicIds(
            long spaceId, long accountId, boolean includeProtected, int offset, int limit);

    long countLibraryImages(long spaceId, long accountId, boolean includeProtected);

    List<YearBucket> findLibraryYears(long spaceId, long accountId, boolean includeProtected);

    List<UUID> findLibraryPublicIdsByYear(
            long spaceId,
            long accountId,
            int year,
            boolean includeProtected,
            int offset,
            int limit);

    long countLibraryImagesByYear(long spaceId, long accountId, int year, boolean includeProtected);

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

    record YearBucket(int year, long mediaCount, UUID coverAssetId) {}

    record AlbumCover(UUID albumId, UUID assetId) {}

    record SystemCatalogRow(String systemKey, long mediaCount, UUID coverAssetId) {}

    record CustomCatalogRow(
            Long groupInternalId,
            UUID groupId,
            String groupName,
            Integer groupSortOrder,
            Long albumInternalId,
            UUID albumId,
            String albumType,
            String albumName,
            String albumDescription,
            UUID coverAssetId,
            long mediaCount) {}

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
