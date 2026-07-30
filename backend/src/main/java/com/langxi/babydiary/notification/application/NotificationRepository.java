package com.langxi.babydiary.notification.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository {
    List<Row> findPage(long accountId, int limit, long offset);

    long count(long accountId);

    long countUnread(long accountId);

    void markRead(long accountId, UUID publicId, LocalDateTime now);

    void markAllRead(long accountId, LocalDateTime now);

    boolean insert(NewNotification notification);

    List<Long> findActiveSpaceMemberIds(long spaceId, Long excludedAccountId);

    record Row(
            UUID id,
            UUID spaceId,
            String type,
            String title,
            String body,
            String targetRefJson,
            LocalDateTime readAt,
            LocalDateTime createdAt) {}

    record NewNotification(
            UUID publicId,
            long accountId,
            Long spaceId,
            String type,
            String title,
            String body,
            String targetRefJson,
            String dedupeKey) {}
}
