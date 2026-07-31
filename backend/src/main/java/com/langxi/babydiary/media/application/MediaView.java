package com.langxi.babydiary.media.application;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record MediaView(
        UUID id,
        UUID spaceId,
        String mediaType,
        String originalFilename,
        String caption,
        LocalDateTime takenAt,
        String accessScope,
        boolean libraryVisible,
        String status,
        LocalDateTime createdAt,
        boolean protectedContent,
        Representations representations) {
    public record Representations(
            Representation original,
            Representation thumbnail,
            Representation poster,
            Representation waveform,
            Representation transcoded) {}

    public record Representation(
            String variantType,
            String profile,
            String url,
            Instant expiresAt,
            String contentType,
            Long sizeBytes,
            Integer width,
            Integer height,
            Long durationMillis) {}
}
