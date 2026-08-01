package com.langxi.babydiary.media.application;

import com.langxi.babydiary.media.domain.MediaAsset;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository {
    List<MediaAsset> findPage(Query query);

    Optional<MediaAsset> findByPublicId(long spaceId, UUID publicId, long accountId);

    Optional<MediaAsset> findByClientUploadId(long spaceId, long ownerId, UUID clientUploadId);

    List<MediaAsset> findByPublicIds(long spaceId, List<UUID> publicIds, long accountId);

    List<MediaAsset> findByPublicIdsInSpace(long spaceId, List<UUID> publicIds);

    Optional<MediaAsset> findInSpace(UUID spaceId, UUID publicId, boolean includeDeleted);

    Optional<MediaAsset.Variant> findVariant(
            long spaceId, UUID publicId, String type, String profile, long accountId);

    Optional<MediaAsset.Variant> findPreferredVariant(
            long spaceId, UUID publicId, String type, long accountId);

    Optional<MediaAsset.Variant> findPublicVariant(
            UUID spaceId, UUID publicId, String type, String profile);

    Optional<MediaAsset.Variant> findPreferredPublicVariant(
            UUID spaceId, UUID publicId, String type);

    long insertAsset(NewAsset asset);

    boolean insertVariant(NewVariant variant);

    boolean reserveStorage(long spaceId, long sizeBytes);

    boolean reserveStorage(UUID spaceId, long sizeBytes);

    void releaseStorage(long spaceId, long sizeBytes);

    void releaseStorage(UUID spaceId, long sizeBytes);

    boolean softDelete(long spaceId, UUID publicId, long accountId, LocalDateTime deletedAt);

    boolean markDeletePending(long spaceId, UUID publicId, long accountId, LocalDateTime deletedAt);

    void finalizeDeletion(long assetId, UUID spaceId, long releasedBytes, LocalDateTime deletedAt);

    void failUpload(long assetId, LocalDateTime failedAt);

    void markReady(long assetId);

    void updateTechnicalMetadata(long assetId, Integer width, Integer height, Long durationMillis);

    void markDerivativeVersion(long assetId, int version);

    boolean hasVariant(long assetId, String type, String profile);

    boolean retireVariant(
            long assetId, String type, String profile, long sizeBytes, LocalDateTime deletedAt);

    List<DerivativeCandidate> findDerivativeCandidates(int targetVersion, int limit);

    ReferenceCounts references(long assetId);

    void removeFavorites(long assetId);

    boolean updateMetadata(
            long spaceId,
            UUID publicId,
            long accountId,
            String caption,
            LocalDateTime takenAt,
            String accessScope,
            boolean libraryVisible);

    Long findActiveMemberAccountId(long spaceId, UUID accountId);

    boolean transferOwnership(long spaceId, UUID assetId, long currentOwnerId, long targetOwnerId);

    record Query(
            long spaceId,
            long accountId,
            String mediaType,
            boolean libraryOnly,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int limit) {}

    record NewAsset(
            UUID publicId,
            long spaceId,
            long ownerId,
            UUID clientUploadId,
            String mediaType,
            String originalFilename,
            String caption,
            LocalDateTime takenAt,
            String accessScope,
            boolean libraryVisible,
            String status) {}

    record NewVariant(
            long assetId,
            String type,
            String profile,
            String storageProvider,
            String storageKey,
            String contentType,
            long sizeBytes,
            byte[] checksumSha256,
            Integer width,
            Integer height,
            Long durationMillis,
            Double qualityScore,
            String status) {}

    record ReferenceCounts(
            long diaries,
            long albums,
            long albumCovers,
            long anniversaries,
            long avatars,
            long aiProposals) {
        public long blockingTotal() {
            return diaries + albums + albumCovers + anniversaries + avatars + aiProposals;
        }
    }

    record DerivativeCandidate(long spaceId, UUID spacePublicId, UUID assetId, long ownerId) {}
}
