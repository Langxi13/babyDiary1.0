package com.langxi.babydiary.album.application;

import com.langxi.babydiary.album.domain.AlbumCatalog;
import com.langxi.babydiary.media.domain.MediaAsset;

final class AlbumProjection {
    private AlbumProjection() {}

    static AlbumCatalog.Album album(
            AlbumRepository.AlbumRow row, java.util.UUID groupId, MediaAsset cover) {
        return new AlbumCatalog.Album(
                row.id(),
                groupId,
                null,
                row.type(),
                row.name(),
                row.description(),
                row.coverAssetId(),
                row.coverVariantType(),
                row.coverVariantProfile(),
                row.mediaCount(),
                cover);
    }

    static MediaAsset.Variant coverVariant(MediaAsset asset) {
        return asset.variants().stream()
                .filter(value -> "READY".equals(value.status()))
                .filter(
                        value ->
                                "THUMBNAIL".equals(value.type()) || "ORIGINAL".equals(value.type()))
                .min(
                        java.util.Comparator.comparingInt(
                                        (MediaAsset.Variant value) ->
                                                "THUMBNAIL".equals(value.type()) ? 0 : 1)
                                .thenComparingInt(
                                        value ->
                                                "default".equals(value.profile())
                                                        ? 0
                                                        : "source".equals(value.profile()) ? 1 : 2)
                                .thenComparing(MediaAsset.Variant::profile))
                .orElse(null);
    }
}
