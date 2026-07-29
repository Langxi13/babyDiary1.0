package com.langxi.babydiary.v3.draft.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

public record DiaryDraft(UUID id, UUID spaceId, String draftKey, UUID diaryId, JsonNode payload,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
}
