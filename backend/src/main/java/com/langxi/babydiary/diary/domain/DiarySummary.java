package com.langxi.babydiary.diary.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DiarySummary(
        long internalId,
        UUID id,
        UUID spaceId,
        long authorId,
        String title,
        LocalDate diaryDate,
        String contentSnippet,
        String mood,
        String visibility,
        boolean locked,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt,
        List<DiaryEntry.TagRef> tags,
        long mediaCount,
        List<Preview> previews) {
    public DiarySummary {
        tags = List.copyOf(tags);
        previews = List.copyOf(previews);
    }

    public record Preview(
            UUID id,
            String mediaType,
            int position,
            String status,
            String thumbnailProfile,
            String previewProfile,
            boolean protectedContent) {}
}
