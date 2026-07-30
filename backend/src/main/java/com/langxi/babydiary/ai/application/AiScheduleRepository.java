package com.langxi.babydiary.ai.application;

import java.time.LocalDateTime;

public interface AiScheduleRepository {
    ScheduleState find(long spaceId);

    void save(
            long spaceId,
            long accountId,
            boolean weeklyEnabled,
            boolean monthlyEnabled,
            boolean annualEnabled,
            LocalDateTime nextRunAt);

    record ScheduleState(
            boolean weeklyEnabled,
            boolean monthlyEnabled,
            boolean annualEnabled,
            LocalDateTime nextRunAt) {}
}
