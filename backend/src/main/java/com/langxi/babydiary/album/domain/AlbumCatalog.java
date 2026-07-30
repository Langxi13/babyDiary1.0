package com.langxi.babydiary.album.domain;

import com.langxi.babydiary.media.domain.MediaAsset;
import java.util.List;
import java.util.UUID;

public record AlbumCatalog(List<Group> groups) {
    public record Group(UUID id, String type, String name, List<Album> albums) {}

    public record Album(
            UUID id,
            UUID groupId,
            String systemKey,
            String type,
            String name,
            String description,
            UUID coverAssetId,
            String coverVariantType,
            String coverVariantProfile,
            long mediaCount,
            MediaAsset coverMedia) {}

    public record Detail(Album album, List<MediaAsset> media, long totalMedia) {}
}
