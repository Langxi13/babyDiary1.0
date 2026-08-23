package com.langxi.babydiary.home.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record HomeProjection(
        long diaryTotal,
        List<Diary> recentDiaries,
        List<Draft> drafts,
        List<Anniversary> anniversaries,
        List<Favorite> favorites) {
    public HomeProjection {
        recentDiaries = List.copyOf(recentDiaries);
        drafts = List.copyOf(drafts);
        anniversaries = List.copyOf(anniversaries);
        favorites = List.copyOf(favorites);
    }

    public record Diary(
            UUID id,
            String title,
            LocalDate diaryDate,
            String contentSnippet,
            String mood,
            String visibility,
            boolean locked,
            int version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<Tag> tags,
            long mediaCount,
            List<Media> previews) {}

    public record Tag(UUID id, String name, String color) {}

    public record Media(
            UUID id,
            String mediaType,
            int position,
            String status,
            boolean protectedContent,
            String originalProfile,
            String thumbnailProfile,
            String previewProfile) {}

    public record Draft(
            UUID id, String draftKey, UUID diaryId, JsonNode payload, LocalDateTime updatedAt) {}

    public record Anniversary(UUID id, String title, LocalDate date) {}

    public record Favorite(
            UUID id,
            String mediaType,
            String status,
            boolean protectedContent,
            String originalProfile,
            String thumbnailProfile,
            String previewProfile) {}
}
