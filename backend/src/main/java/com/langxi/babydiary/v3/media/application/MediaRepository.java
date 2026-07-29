package com.langxi.babydiary.v3.media.application;

import com.langxi.babydiary.v3.media.domain.MediaAsset;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository {
    List<MediaAsset> findPage(Query query);

    Optional<MediaAsset> findByPublicId(long spaceId, UUID publicId, long accountId);

    List<MediaAsset> findByPublicIds(long spaceId, List<UUID> publicIds, long accountId);

    Optional<MediaAsset.Variant> findVariant(long spaceId, UUID publicId, String type, String profile,
                                              long accountId);

    Optional<MediaAsset.Variant> findPublicVariant(UUID spaceId, UUID publicId, String type, String profile);

    long insertAsset(NewAsset asset);

    void insertVariant(NewVariant variant);

    boolean reserveStorage(long spaceId, long sizeBytes);

    void releaseStorage(long spaceId, long sizeBytes);

    boolean softDelete(long spaceId, UUID publicId, long accountId, LocalDateTime deletedAt);

    boolean updateMetadata(long spaceId, UUID publicId, long accountId, String caption,
                           LocalDateTime takenAt, String accessScope, boolean libraryVisible);

    record Query(long spaceId, long accountId, String mediaType, boolean libraryOnly,
                 LocalDateTime cursorCreatedAt, Long cursorId, int limit) {
    }

    record NewAsset(UUID publicId, long spaceId, long ownerId, String mediaType, String originalFilename,
                    String caption, LocalDateTime takenAt, String accessScope, boolean libraryVisible,
                    String status) {
    }

    record NewVariant(long assetId, String type, String profile, String storageProvider, String storageKey,
                      String contentType, long sizeBytes, byte[] checksumSha256, String status) {
    }
}
