package com.langxi.babydiary.ai.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AiScheduleRepository {
    ScheduleState find(long spaceId);

    void save(
            long spaceId,
            long accountId,
            boolean weeklyEnabled,
            boolean monthlyEnabled,
            boolean annualEnabled,
            LocalDateTime nextRunAt);

    List<DueSchedule> findDue(LocalDateTime now, int limit);

    boolean claim(long spaceId, LocalDateTime expectedRunAt, LocalDateTime nextRunAt);

    record ScheduleState(
            boolean weeklyEnabled,
            boolean monthlyEnabled,
            boolean annualEnabled,
            LocalDateTime nextRunAt) {}

    record DueSchedule(
            long spaceInternalId,
            UUID spaceId,
            long accountId,
            boolean weeklyEnabled,
            boolean monthlyEnabled,
            boolean annualEnabled,
            LocalDateTime nextRunAt) {}
}
