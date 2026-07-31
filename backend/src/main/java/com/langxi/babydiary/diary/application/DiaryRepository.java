package com.langxi.babydiary.diary.application;

import com.langxi.babydiary.diary.domain.DiaryEntry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiaryRepository {
    List<DiaryEntry> findPage(Query query);

    long count(Query query);

    Optional<DiaryEntry> findByPublicId(
            long spaceId, UUID diaryId, long accountId, boolean includeDeleted);

    long insert(NewDiary diary);

    int update(long diaryId, int expectedVersion, UpdatedDiary diary);

    int setDeleted(long diaryId, int expectedVersion, LocalDateTime deletedAt);

    boolean permanentlyDelete(long diaryId, int expectedVersion);

    List<PurgeCandidate> findPurgeCandidates(LocalDateTime deletedBefore, int limit);

    List<Long> resolveTagIds(long spaceId, List<UUID> publicIds);

    List<Long> resolveMediaIds(long spaceId, long accountId, boolean locked, List<UUID> publicIds);

    void replaceTags(long spaceId, long diaryId, List<Long> tagIds);

    void replaceMedia(long spaceId, long diaryId, List<Long> assetIds);

    void insertRevision(
            long diaryId, int version, long editorId, String snapshotJson, LocalDateTime createdAt);

    Optional<Revision> findRevision(long diaryId, UUID revisionId);

    List<RevisionSummary> findRevisions(long diaryId);

    record Query(
            long spaceId,
            long accountId,
            LocalDate startDate,
            LocalDate endDate,
            String keyword,
            String mood,
            UUID tagId,
            boolean trash,
            boolean elevated,
            LocalDate cursorDate,
            Long cursorId,
            int limit) {}

    record NewDiary(
            UUID publicId,
            long spaceId,
            long authorId,
            String title,
            LocalDate diaryDate,
            String contentHtml,
            String contentText,
            String mood,
            String visibility,
            boolean locked) {}

    record UpdatedDiary(
            String title,
            LocalDate diaryDate,
            String contentHtml,
            String contentText,
            String mood,
            String visibility,
            boolean locked) {}

    record Revision(
            UUID id,
            int version,
            UUID editorId,
            String editorName,
            String snapshotJson,
            LocalDateTime createdAt) {}

    record RevisionSummary(
            UUID id, int version, UUID editorId, String editorName, LocalDateTime createdAt) {}

    record PurgeCandidate(
            long internalId,
            UUID id,
            long spaceInternalId,
            UUID spaceId,
            long authorId,
            String visibility,
            int version) {}
}
