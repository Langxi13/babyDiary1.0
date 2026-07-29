package com.langxi.babydiary.v3.reminder.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ReminderRepository {
    List<Row> findForAccount(long spaceId, long accountId);

    void upsert(NewReminder reminder);

    record Row(UUID id, String type, boolean enabled, JsonNode schedule, LocalDateTime nextRunAt,
               LocalDateTime lastRunAt) {
    }

    record NewReminder(byte[] publicId, long accountId, long spaceId, String type, boolean enabled,
                       String scheduleJson, LocalDateTime nextRunAt) {
    }
}
