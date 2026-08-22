package com.langxi.babydiary.transfer.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TransferRepository {
    String findSpaceName(long spaceId);

    List<DiaryData> findDiaries(
            long spaceId, long accountId, LocalDate startDate, LocalDate endDate, int limit);

    ExportPreflight exportPreflight(long spaceId, long accountId);

    List<DiaryData> findDiaryBatch(
            long spaceId, long accountId, LocalDate afterDate, Long afterId, int limit);

    List<TagData> findTags(List<Long> diaryIds);

    List<MediaData> findMedia(List<Long> diaryIds);

    List<CommentData> findComments(List<Long> diaryIds);

    boolean diaryExists(long spaceId, byte[] publicId);

    record ExportPreflight(
            long diaryCount,
            long mediaCount,
            long totalMediaBytes,
            long maxMediaBytes,
            boolean requiresStepUp) {}

    record DiaryData(
            long diaryId,
            byte[] publicId,
            String title,
            LocalDate diaryDate,
            String contentHtml,
            String moodKey,
            String visibility,
            boolean locked) {}

    record TagData(long diaryId, String name, String color) {}

    record MediaData(
            long diaryId,
            byte[] publicId,
            String originalFilename,
            String mediaType,
            String caption,
            LocalDateTime takenAt,
            int position) {}

    record CommentData(long diaryId, String username, String content, LocalDateTime createdAt) {}
}
