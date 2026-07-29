package com.langxi.babydiary.v3.media.infrastructure;

import com.langxi.babydiary.v3.media.application.MediaRepository;
import com.langxi.babydiary.v3.media.domain.MediaAsset;
import com.langxi.babydiary.v3.platform.application.BinaryUuid;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisMediaRepository implements MediaRepository {
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
        return hydrate(mapper.findByPublicId(spaceId, BinaryUuid.toBytes(publicId), accountId)).stream().findFirst();
    }

    @Override
    public List<MediaAsset> findByPublicIds(long spaceId, List<UUID> publicIds, long accountId) {
        if (publicIds == null || publicIds.isEmpty()) return List.of();
        Map<UUID, MediaAsset> hydrated = hydrate(mapper.findByPublicIds(spaceId,
                publicIds.stream().map(BinaryUuid::toBytes).toList(), accountId)).stream()
                .collect(java.util.stream.Collectors.toMap(MediaAsset::id, value -> value));
        return publicIds.stream().map(hydrated::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public Optional<MediaAsset.Variant> findVariant(long spaceId, UUID publicId, String type, String profile,
                                                    long accountId) {
        return Optional.ofNullable(mapper.findVariant(spaceId, BinaryUuid.toBytes(publicId), type, profile, accountId))
                .map(this::variant);
    }

    @Override
    public Optional<MediaAsset.Variant> findPublicVariant(UUID spaceId, UUID publicId, String type, String profile) {
        return Optional.ofNullable(mapper.findPublicVariant(BinaryUuid.toBytes(spaceId), BinaryUuid.toBytes(publicId),
                        type, profile))
                .map(this::variant);
    }

    @Override
    public long insertAsset(NewAsset asset) {
        MediaMapper.AssetInsert row = new MediaMapper.AssetInsert(BinaryUuid.toBytes(asset.publicId()), asset.spaceId(),
                asset.ownerId(), asset.mediaType(), asset.originalFilename(), asset.caption(), asset.takenAt(),
                asset.accessScope(), asset.libraryVisible(), asset.status());
        mapper.insertAsset(row);
        if (row.getAssetId() == null) throw new IllegalStateException("Media insert returned no ID");
        return row.getAssetId();
    }

    @Override
    public void insertVariant(NewVariant variant) {
        mapper.insertVariant(variant);
    }

    @Override
    public boolean reserveStorage(long spaceId, long sizeBytes) {
        return mapper.reserveStorage(spaceId, sizeBytes) == 1;
    }

    @Override
    public void releaseStorage(long spaceId, long sizeBytes) {
        if (sizeBytes > 0) mapper.releaseStorage(spaceId, sizeBytes);
    }

    @Override
    public boolean softDelete(long spaceId, UUID publicId, long accountId, LocalDateTime deletedAt) {
        return mapper.softDelete(spaceId, BinaryUuid.toBytes(publicId), accountId, deletedAt) == 1;
    }

    @Override
    public boolean updateMetadata(long spaceId, UUID publicId, long accountId, String caption,
                                  LocalDateTime takenAt, String accessScope, boolean libraryVisible) {
        return mapper.updateMetadata(spaceId, BinaryUuid.toBytes(publicId), accountId, caption, takenAt,
                accessScope, libraryVisible) == 1;
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
        return new MediaAsset.Variant(row.variantType(), row.profile(), row.storageProvider(), row.storageKey(),
                row.contentType(), row.sizeBytes(), row.checksumSha256(), row.width(), row.height(),
                row.durationMillis(), row.variantStatus());
    }

    private MediaAsset.Variant variant(MediaMapper.VariantRow row) {
        return new MediaAsset.Variant(row.variantType(), row.profile(), row.storageProvider(), row.storageKey(),
                row.contentType(), row.sizeBytes(), row.checksumSha256(), row.width(), row.height(),
                row.durationMillis(), row.status());
    }

    private static final class Builder {
        private final MediaMapper.MediaRow row;
        private final List<MediaAsset.Variant> variants = new ArrayList<>();

        private Builder(MediaMapper.MediaRow row) {
            this.row = row;
        }

        private MediaAsset build() {
            return new MediaAsset(row.assetId(), BinaryUuid.fromBytes(row.publicId()),
                    BinaryUuid.fromBytes(row.spacePublicId()), row.ownerId(), row.mediaType(),
                    row.originalFilename(), row.caption(), row.takenAt(), row.accessScope(), row.libraryVisible(),
                    row.assetStatus(), row.assetCreatedAt(), row.assetUpdatedAt(), List.copyOf(variants));
        }
    }
}
