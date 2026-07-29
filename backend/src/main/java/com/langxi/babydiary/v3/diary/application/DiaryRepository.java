package com.langxi.babydiary.v3.diary.application;

import com.langxi.babydiary.v3.diary.domain.DiaryEntry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiaryRepository {
    List<DiaryEntry> findPage(Query query);

    long count(Query query);

    Optional<DiaryEntry> findByPublicId(long spaceId, UUID diaryId, long accountId, boolean includeDeleted);

    long insert(NewDiary diary);

    int update(long diaryId, int expectedVersion, UpdatedDiary diary);

    int setDeleted(long diaryId, int expectedVersion, LocalDateTime deletedAt);

    List<Long> resolveTagIds(long spaceId, List<UUID> publicIds);

    List<Long> resolveMediaIds(long spaceId, List<UUID> publicIds);

    void replaceTags(long spaceId, long diaryId, List<Long> tagIds);

    void replaceMedia(long spaceId, long diaryId, List<Long> assetIds);

    void insertRevision(long diaryId, int version, long editorId, String snapshotJson, LocalDateTime createdAt);

    Optional<Revision> findRevision(long diaryId, long revisionId);

    List<RevisionSummary> findRevisions(long diaryId);

    record Query(long spaceId, long accountId, LocalDate startDate, LocalDate endDate, String keyword,
                 String mood, UUID tagId, boolean trash, LocalDate cursorDate, Long cursorId, int limit) {
    }

    record NewDiary(UUID publicId, long spaceId, long authorId, String title, LocalDate diaryDate,
                    String contentHtml, String contentText, String mood, String visibility, boolean locked) {
    }

    record UpdatedDiary(String title, LocalDate diaryDate, String contentHtml, String contentText,
                        String mood, String visibility, boolean locked) {
    }

    record Revision(long id, int version, long editorId, String snapshotJson, LocalDateTime createdAt) {
    }

    record RevisionSummary(long id, int version, long editorId, LocalDateTime createdAt) {
    }
}
