package com.langxi.babydiary.v3.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final ObjectMapper json;

    public NotificationService(NotificationRepository notifications, ObjectMapper json) {
        this.notifications = notifications;
        this.json = json;
    }

    public Page list(long accountId, int page, int size) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.max(1, Math.min(size, 50));
        List<Item> items = notifications.findPage(accountId, normalizedSize, (long) normalizedPage * normalizedSize)
                .stream().map(this::item).toList();
        return new Page(items, normalizedPage, normalizedSize, notifications.count(accountId));
    }

    public long unread(long accountId) {
        return notifications.countUnread(accountId);
    }

    @Transactional
    public void markRead(long accountId, UUID notificationId) {
        notifications.markRead(accountId, notificationId, LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public void markAllRead(long accountId) {
        notifications.markAllRead(accountId, LocalDateTime.now(ZoneOffset.UTC));
    }

    private Item item(NotificationRepository.Row row) {
        try {
            JsonNode target = row.targetRefJson() == null ? null : json.readTree(row.targetRefJson());
            return new Item(row.id(), row.spaceId(), row.type(), row.title(), row.body(), target,
                    row.readAt(), row.createdAt());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored notification target is invalid", exception);
        }
    }

    public record Item(UUID id, UUID spaceId, String type, String title, String body, JsonNode target,
                       LocalDateTime readAt, LocalDateTime createdAt) {
    }

    public record Page(List<Item> items, int page, int size, long total) {
    }
}
