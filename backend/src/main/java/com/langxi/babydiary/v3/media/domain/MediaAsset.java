package com.langxi.babydiary.v3.media.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record MediaAsset(long internalId, UUID id, UUID spaceId, long ownerId, String mediaType,
                         String originalFilename, String caption, LocalDateTime takenAt,
                         String accessScope, boolean libraryVisible, String status,
                         LocalDateTime createdAt, LocalDateTime updatedAt, List<Variant> variants) {
    public record Variant(String type, String profile, String storageProvider, String storageKey,
                          String contentType, long sizeBytes, byte[] checksumSha256, Integer width,
                          Integer height, Long durationMillis, String status) {
    }
}
