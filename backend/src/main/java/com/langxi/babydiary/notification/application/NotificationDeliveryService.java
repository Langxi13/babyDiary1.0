package com.langxi.babydiary.notification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.platform.application.BackgroundJobQueue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryService {
    private final NotificationRepository notifications;
    private final BackgroundJobQueue jobs;
    private final ObjectMapper json;
    private final PushGateway push;

    public NotificationDeliveryService(
            NotificationRepository notifications,
            BackgroundJobQueue jobs,
            ObjectMapper json,
            PushGateway push) {
        this.notifications = notifications;
        this.jobs = jobs;
        this.json = json;
        this.push = push;
    }

    @Transactional
    public boolean notifyUser(
            long accountId,
            Long spaceId,
            String type,
            String title,
            String body,
            String targetPath,
            String dedupeKey) {
        if (dedupeKey == null || dedupeKey.isBlank()) {
            throw new IllegalArgumentException("Notification dedupe key is required");
        }
        boolean inserted =
                notifications.insert(
                        new NotificationRepository.NewNotification(
                                UUID.randomUUID(),
                                accountId,
                                spaceId,
                                type,
                                title,
                                body,
                                target(targetPath),
                                dedupeKey));
        if (!inserted) return false;

        if (push.configured()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("accountId", accountId);
            payload.put("title", title);
            if (body != null) payload.put("body", body);
            if (targetPath != null) payload.put("targetPath", targetPath);
            jobs.enqueue(
                    spaceId, accountId, "PUSH_DELIVERY", "notification:" + dedupeKey, payload, 5);
        }
        return true;
    }

    private String target(String path) {
        if (path == null || path.isBlank()) return null;
        try {
            return json.writeValueAsString(Map.of("path", path));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Notification target is invalid", exception);
        }
    }
}
