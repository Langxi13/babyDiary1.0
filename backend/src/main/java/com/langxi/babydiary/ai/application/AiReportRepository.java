package com.langxi.babydiary.ai.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiReportRepository {
    List<Report> findPage(long spaceId, long creatorId, String periodType, int offset, int limit);

    long count(long spaceId, long creatorId, String periodType);

    Optional<Report> findByPublicId(long spaceId, long creatorId, UUID publicId);

    Optional<Report> findByPeriod(
            long spaceId, long creatorId, String periodType, LocalDate start, LocalDate end);

    boolean delete(long spaceId, long creatorId, UUID publicId);

    long insert(NewReport report);

    void insertDiary(long spaceId, long reportId, long diaryId);

    List<DiaryInput> findDiaries(long spaceId, long accountId, LocalDate start, LocalDate end);

    record Report(
            long internalId,
            UUID id,
            UUID spaceId,
            String periodType,
            LocalDate start,
            LocalDate end,
            String title,
            String markdown,
            int diaryCount,
            String model,
            LocalDateTime createdAt) {}

    record NewReport(
            UUID publicId,
            long spaceId,
            long createdBy,
            String periodType,
            LocalDate start,
            LocalDate end,
            String title,
            String markdown,
            int diaryCount,
            String model) {}

    record DiaryInput(
            long internalId,
            UUID id,
            LocalDate date,
            String title,
            String contentText,
            String mood) {}
}
