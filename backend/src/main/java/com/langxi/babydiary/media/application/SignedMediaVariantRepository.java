package com.langxi.babydiary.media.application;

import com.langxi.babydiary.media.domain.MediaAsset;
import java.util.Optional;
import java.util.UUID;

public interface SignedMediaVariantRepository {
    Optional<Resolved> resolve(
            UUID spaceId, UUID assetId, String type, String profile, MediaAccessContext context);

    record Resolved(boolean protectedContent, MediaAsset.Variant variant) {}
}
