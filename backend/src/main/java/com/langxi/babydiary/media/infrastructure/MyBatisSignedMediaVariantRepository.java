package com.langxi.babydiary.media.infrastructure;

import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.SignedMediaVariantRepository;
import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.platform.application.BinaryUuid;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSignedMediaVariantRepository implements SignedMediaVariantRepository {
    private final SignedMediaMapper mapper;

    public MyBatisSignedMediaVariantRepository(SignedMediaMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Resolved> resolve(
            UUID spaceId, UUID assetId, String type, String profile, MediaAccessContext context) {
        SignedMediaMapper.VariantRow row =
                mapper.resolve(
                        BinaryUuid.toBytes(spaceId),
                        BinaryUuid.toBytes(assetId),
                        type,
                        profile,
                        context.source().name(),
                        context.contextId() == null
                                ? null
                                : BinaryUuid.toBytes(context.contextId()),
                        context.accountId());
        if (row == null) return Optional.empty();
        return Optional.of(
                new Resolved(
                        row.protectedContent(),
                        new MediaAsset.Variant(
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
                                row.status())));
    }
}
