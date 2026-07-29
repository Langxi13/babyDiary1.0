package com.langxi.babydiary.v3.diary.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DiaryEntry(
        long internalId,
        UUID id,
        UUID spaceId,
        long authorId,
        String title,
        LocalDate diaryDate,
        String contentHtml,
        String contentText,
        String mood,
        String visibility,
        boolean locked,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt,
        List<TagRef> tags,
        List<MediaRef> media
) {
    public DiaryEntry {
        tags = List.copyOf(tags);
        media = List.copyOf(media);
    }

    public record TagRef(UUID id, String name, String color) {
    }

    public record MediaRef(UUID id, String mediaType, String caption, LocalDateTime takenAt,
                           int position, String status) {
    }
}
