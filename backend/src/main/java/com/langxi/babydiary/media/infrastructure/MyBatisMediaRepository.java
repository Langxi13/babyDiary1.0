package com.langxi.babydiary.media.infrastructure;

import com.langxi.babydiary.media.application.MediaRepository;
import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.platform.application.BinaryUuid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisMediaRepository implements MediaRepository {
    private static final int ID_BATCH_SIZE = 500;
    private final MediaMapper mapper;

    public MyBatisMediaRepository(MediaMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<MediaAsset> findPage(Query query) {
        return hydrate(mapper.findPage(query));
    }

    @Override
    public Optional<MediaAsset> findByPublicId(long spaceId, UUID publicId, long accountId) {
        return hydrate(mapper.findByPublicId(spaceId, BinaryUuid.toBytes(publicId), accountId))
                .stream()
                .findFirst();
    }

    @Override
    public List<MediaAsset> findByPublicIds(long spaceId, List<UUID> publicIds, long accountId) {
        if (publicIds == null || publicIds.isEmpty()) return List.of();
        Map<UUID, MediaAsset> hydrated = new LinkedHashMap<>();
        for (int start = 0; start < publicIds.size(); start += ID_BATCH_SIZE) {
            List<UUID> batch =
                    publicIds.subList(start, Math.min(start + ID_BATCH_SIZE, publicIds.size()));
            hydrate(
                            mapper.findByPublicIds(
                                    spaceId,
                                    batch.stream().map(BinaryUuid::toBytes).toList(),
                                    accountId))
                    .forEach(value -> hydrated.put(value.id(), value));
        }
        return publicIds.stream().map(hydrated::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public List<MediaAsset> findByPublicIdsInSpace(long spaceId, List<UUID> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) return List.of();
        Map<UUID, MediaAsset> hydrated = new LinkedHashMap<>();
        for (int start = 0; start < publicIds.size(); start += ID_BATCH_SIZE) {
            List<UUID> batch =
                    publicIds.subList(start, Math.min(start + ID_BATCH_SIZE, publicIds.size()));
            hydrate(
                            mapper.findByPublicIdsInSpace(
                                    spaceId, batch.stream().map(BinaryUuid::toBytes).toList()))
                    .forEach(value -> hydrated.put(value.id(), value));
        }
        return publicIds.stream().map(hydrated::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public Optional<MediaAsset> findInSpace(UUID spaceId, UUID publicId, boolean includeDeleted) {
        return hydrate(
                        mapper.findInSpace(
                                BinaryUuid.toBytes(spaceId),
                                BinaryUuid.toBytes(publicId),
                                includeDeleted))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<MediaAsset.Variant> findVariant(
            long spaceId, UUID publicId, String type, String profile, long accountId) {
        return Optional.ofNullable(
                        mapper.findVariant(
                                spaceId, BinaryUuid.toBytes(publicId), type, profile, accountId))
                .map(this::variant);
    }

    @Override
    public Optional<MediaAsset.Variant> findPreferredVariant(
            long spaceId, UUID publicId, String type, long accountId) {
        return Optional.ofNullable(
                        mapper.findPreferredVariant(
                                spaceId, BinaryUuid.toBytes(publicId), type, accountId))
                .map(this::variant);
    }

    @Override
    public Optional<MediaAsset.Variant> findPublicVariant(
            UUID spaceId, UUID publicId, String type, String profile) {
        return Optional.ofNullable(
                        mapper.findPublicVariant(
                                BinaryUuid.toBytes(spaceId),
                                BinaryUuid.toBytes(publicId),
                                type,
                                profile))
                .map(this::variant);
    }

    @Override
    public Optional<MediaAsset.Variant> findPreferredPublicVariant(
            UUID spaceId, UUID publicId, String type) {
        return Optional.ofNullable(
                        mapper.findPreferredPublicVariant(
                                BinaryUuid.toBytes(spaceId), BinaryUuid.toBytes(publicId), type))
                .map(this::variant);
    }

    @Override
    public long insertAsset(NewAsset asset) {
        MediaMapper.AssetInsert row =
                new MediaMapper.AssetInsert(
                        BinaryUuid.toBytes(asset.publicId()),
                        asset.spaceId(),
                        asset.ownerId(),
                        asset.mediaType(),
                        asset.originalFilename(),
                        asset.caption(),
                        asset.takenAt(),
                        asset.accessScope(),
                        asset.libraryVisible(),
                        asset.status());
        mapper.insertAsset(row);
        if (row.getAssetId() == null)
            throw new IllegalStateException("Media insert returned no ID");
        return row.getAssetId();
    }

    @Override
    public boolean insertVariant(NewVariant variant) {
        return mapper.insertVariant(variant) == 1;
    }

    @Override
    public boolean reserveStorage(long spaceId, long sizeBytes) {
        return mapper.reserveStorage(spaceId, sizeBytes) == 1;
    }

    @Override
    public boolean reserveStorage(UUID spaceId, long sizeBytes) {
        return mapper.reserveStorageByPublicId(BinaryUuid.toBytes(spaceId), sizeBytes) == 1;
    }

    @Override
    public void releaseStorage(long spaceId, long sizeBytes) {
        if (sizeBytes > 0) mapper.releaseStorage(spaceId, sizeBytes);
    }

    @Override
    public void releaseStorage(UUID spaceId, long sizeBytes) {
        if (sizeBytes > 0) mapper.releaseStorageByPublicId(BinaryUuid.toBytes(spaceId), sizeBytes);
    }

    @Override
    public boolean softDelete(
            long spaceId, UUID publicId, long accountId, LocalDateTime deletedAt) {
        return mapper.softDelete(spaceId, BinaryUuid.toBytes(publicId), accountId, deletedAt) == 1;
    }

    @Override
    public boolean markDeletePending(
            long spaceId, UUID publicId, long accountId, LocalDateTime deletedAt) {
        return mapper.markDeletePending(spaceId, BinaryUuid.toBytes(publicId), accountId, deletedAt)
                == 1;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void finalizeDeletion(
            long assetId, UUID spaceId, long releasedBytes, LocalDateTime deletedAt) {
        mapper.markVariantsDeleted(assetId, deletedAt);
        mapper.markAssetDeleted(assetId, deletedAt);
        if (releasedBytes > 0)
            mapper.releaseStorageByPublicId(BinaryUuid.toBytes(spaceId), releasedBytes);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void failUpload(long assetId, LocalDateTime failedAt) {
        mapper.markVariantsDeleted(assetId, failedAt);
        mapper.failUpload(assetId, failedAt);
    }

    @Override
    public void markReady(long assetId) {
        mapper.markReady(assetId);
    }

    @Override
    public void updateTechnicalMetadata(
            long assetId, Integer width, Integer height, Long durationMillis) {
        mapper.updateTechnicalMetadata(assetId, width, height, durationMillis);
    }

    @Override
    public void markDerivativeVersion(long assetId, int version) {
        mapper.markDerivativeVersion(assetId, version);
    }

    @Override
    public boolean hasVariant(long assetId, String type, String profile) {
        return mapper.hasVariant(assetId, type, profile);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public boolean retireVariant(
            long assetId, String type, String profile, long sizeBytes, LocalDateTime deletedAt) {
        return mapper.retireVariant(assetId, type, profile, sizeBytes, deletedAt) > 0;
    }

    @Override
    public List<DerivativeCandidate> findDerivativeCandidates(int targetVersion, int limit) {
        return mapper
                .findDerivativeCandidates(targetVersion, Math.max(1, Math.min(limit, 50)))
                .stream()
                .map(
                        row ->
                                new DerivativeCandidate(
                                        row.spaceId(),
                                        BinaryUuid.fromBytes(row.spacePublicId()),
                                        BinaryUuid.fromBytes(row.assetPublicId()),
                                        row.ownerId()))
                .toList();
    }

    @Override
    public ReferenceCounts references(long assetId) {
        return mapper.references(assetId);
    }

    @Override
    public void removeFavorites(long assetId) {
        mapper.removeFavorites(assetId);
    }

    @Override
    public boolean updateMetadata(
            long spaceId,
            UUID publicId,
            long accountId,
            String caption,
            LocalDateTime takenAt,
            String accessScope,
            boolean libraryVisible) {
        return mapper.updateMetadata(
                        spaceId,
                        BinaryUuid.toBytes(publicId),
                        accountId,
                        caption,
                        takenAt,
                        accessScope,
                        libraryVisible)
                == 1;
    }

    @Override
    public Long findActiveMemberAccountId(long spaceId, UUID accountId) {
        return mapper.findActiveMemberAccountId(spaceId, BinaryUuid.toBytes(accountId));
    }

    @Override
    public boolean transferOwnership(
            long spaceId, UUID assetId, long currentOwnerId, long targetOwnerId) {
        return mapper.transferOwnership(
                        spaceId, BinaryUuid.toBytes(assetId), currentOwnerId, targetOwnerId)
                == 1;
    }

    private List<MediaAsset> hydrate(List<MediaMapper.MediaRow> rows) {
        Map<Long, Builder> values = new LinkedHashMap<>();
        for (MediaMapper.MediaRow row : rows) {
            Builder builder = values.computeIfAbsent(row.assetId(), ignored -> new Builder(row));
            if (row.variantType() != null) builder.variants.add(variant(row));
        }
        return values.values().stream().map(Builder::build).toList();
    }

    private MediaAsset.Variant variant(MediaMapper.MediaRow row) {
        return new MediaAsset.Variant(
                row.variantType(),
                row.profile(),
                row.storageProvider(),
                row.storageKey(),
                row.contentType(),
                row.sizeBytes(),
                row.checksumSha256(),
                row.width(),
                row.height(),
                row.durationMillis(),
                row.qualityScore(),
                row.variantStatus());
    }

    private MediaAsset.Variant variant(MediaMapper.VariantRow row) {
        return new MediaAsset.Variant(
                row.variantType(),
                row.profile(),
                row.storageProvider(),
                row.storageKey(),
                row.contentType(),
                row.sizeBytes(),
                row.checksumSha256(),
                row.width(),
                row.height(),
                row.durationMillis(),
                row.qualityScore(),
                row.status());
    }

    private static final class Builder {
        private final MediaMapper.MediaRow row;
        private final List<MediaAsset.Variant> variants = new ArrayList<>();

        private Builder(MediaMapper.MediaRow row) {
            this.row = row;
        }

        private MediaAsset build() {
            return new MediaAsset(
                    row.assetId(),
                    BinaryUuid.fromBytes(row.publicId()),
                    BinaryUuid.fromBytes(row.spacePublicId()),
                    row.ownerId(),
                    row.mediaType(),
                    row.originalFilename(),
                    row.caption(),
                    row.takenAt(),
                    row.accessScope(),
                    row.libraryVisible(),
                    row.assetStatus(),
                    row.derivativeVersion(),
                    row.assetCreatedAt(),
                    row.assetUpdatedAt(),
                    List.copyOf(variants));
        }
    }
}
