package com.langxi.babydiary.reminder.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ReminderRepository {
    List<Row> findForAccount(long spaceId, long accountId);

    void upsert(NewReminder reminder);

    List<DueReminder> findDue(LocalDateTime now, int limit);

    boolean claim(long reminderId, LocalDateTime expectedRunAt, LocalDateTime nextRunAt);

    void disable(long reminderId, LocalDateTime expectedRunAt);

    record Row(
            UUID id,
            String type,
            boolean enabled,
            JsonNode schedule,
            LocalDateTime nextRunAt,
            LocalDateTime lastRunAt) {}

    record NewReminder(
            byte[] publicId,
            long accountId,
            long spaceId,
            String type,
            boolean enabled,
            String scheduleJson,
            LocalDateTime nextRunAt) {}

    record DueReminder(
            long internalId,
            UUID id,
            long accountId,
            long spaceInternalId,
            UUID spaceId,
            String spaceName,
            String type,
            JsonNode schedule,
            LocalDateTime nextRunAt) {}
}
