package com.langxi.babydiary.v3.draft.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DraftRepository {
    List<Row> findForOwner(long spaceId, long ownerId);

    Optional<Row> findByKey(long spaceId, long ownerId, String draftKey);

    void upsert(NewDraft draft);

    void delete(long spaceId, long ownerId, String draftKey);

    record Row(UUID id, UUID spaceId, String draftKey, UUID diaryId, String payloadJson,
               LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    record NewDraft(UUID publicId, long spaceId, long ownerId, Long diaryId, String draftKey, String payloadJson) {
    }
}
