package com.langxi.babydiary.share.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PrivateShareRepository {
    DiaryData findManageableDiary(
            long spaceId, byte[] diaryPublicId, long accountId, boolean spaceOwner);

    void insert(NewShare share);

    List<ShareData> findActive(long diaryId, long accountId);

    OpenShare findForOpen(byte[] tokenHash);

    int incrementView(long shareId, LocalDateTime now);

    int revoke(byte[] publicId, long accountId);

    List<MediaLink> findMedia(long diaryId);

    record NewShare(
            byte[] publicId,
            byte[] tokenHash,
            long spaceId,
            long diaryId,
            long createdBy,
            String passwordHash,
            LocalDateTime expiresAt,
            Integer maxViews) {}

    record DiaryData(long diaryId, boolean locked) {}

    record ShareData(
            long shareId,
            byte[] publicId,
            String passwordHash,
            LocalDateTime expiresAt,
            Integer maxViews,
            int viewCount,
            LocalDateTime createdAt) {}

    record OpenShare(
            long shareId,
            byte[] publicId,
            String passwordHash,
            LocalDateTime expiresAt,
            Integer maxViews,
            int viewCount,
            long spaceInternalId,
            UUID spaceId,
            long diaryId,
            boolean locked,
            String title,
            LocalDate diaryDate,
            String contentHtml,
            String moodKey) {}

    record MediaLink(
            byte[] publicId,
            String mediaType,
            String caption,
            LocalDateTime takenAt,
            int position) {}
}
