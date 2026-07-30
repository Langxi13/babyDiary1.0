package com.langxi.babydiary.transfer.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TransferRepository {
    String findSpaceName(long spaceId);

    List<DiaryData> findDiaries(
            long spaceId, long accountId, LocalDate startDate, LocalDate endDate, int limit);

    List<TagData> findTags(List<Long> diaryIds);

    List<MediaData> findMedia(List<Long> diaryIds);

    List<CommentData> findComments(List<Long> diaryIds);

    boolean diaryExists(long spaceId, byte[] publicId);

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
