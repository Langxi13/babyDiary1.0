package com.langxi.babydiary.ai.application;

import com.langxi.babydiary.platform.application.BackgroundJobQueue;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiScheduledReportDispatcher {
    private final AiScheduleRepository schedules;
    private final BackgroundJobQueue jobs;

    public AiScheduledReportDispatcher(AiScheduleRepository schedules, BackgroundJobQueue jobs) {
        this.schedules = schedules;
        this.jobs = jobs;
    }

    @Transactional
    public boolean dispatch(
            AiScheduleRepository.DueSchedule schedule,
            LocalDateTime nextRunAt,
            List<ReportPeriod> periods) {
        if (!schedules.claim(schedule.spaceInternalId(), schedule.nextRunAt(), nextRunAt)) {
            return false;
        }
        for (ReportPeriod period : periods) {
            jobs.enqueue(
                    schedule.spaceInternalId(),
                    schedule.accountId(),
                    "AI_REPORT",
                    "scheduled:" + schedule.spaceId() + ":" + period.type() + ":" + period.period(),
                    Map.of(
                            "spaceId", schedule.spaceId().toString(),
                            "accountId", schedule.accountId(),
                            "type", period.type(),
                            "period", period.period()),
                    3);
        }
        return true;
    }

    public record ReportPeriod(String type, String period) {}
}
