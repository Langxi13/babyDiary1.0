package com.langxi.babydiary.ai.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AiScheduleWorker {
    private static final Logger log = LoggerFactory.getLogger(AiScheduleWorker.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final AiScheduleRepository schedules;
    private final AiScheduledReportDispatcher dispatcher;
    private final boolean enabled;

    public AiScheduleWorker(
            AiScheduleRepository schedules,
            AiScheduledReportDispatcher dispatcher,
            @Value("${app.ai-schedules.enabled:true}") boolean enabled) {
        this.schedules = schedules;
        this.dispatcher = dispatcher;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.ai-schedules.poll-delay-ms:60000}")
    public void runDue() {
        if (!enabled) return;
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        LocalDate today = now.toLocalDate();
        LocalDateTime nextRunAt = nextRun(now);
        for (AiScheduleRepository.DueSchedule schedule :
                schedules.findDue(LocalDateTime.now(ZoneOffset.UTC), 20)) {
            try {
                LocalDate dueDate =
                        schedule.nextRunAt()
                                .atOffset(ZoneOffset.UTC)
                                .atZoneSameInstant(ZONE)
                                .toLocalDate();
                dispatcher.dispatch(schedule, nextRunAt, periods(schedule, dueDate, today));
            } catch (RuntimeException exception) {
                log.warn(
                        "Scheduled AI report dispatch failed for space {}",
                        schedule.spaceId(),
                        exception);
            }
        }
    }

    List<AiScheduledReportDispatcher.ReportPeriod> periods(
            AiScheduleRepository.DueSchedule schedule, LocalDate dueDate, LocalDate today) {
        List<AiScheduledReportDispatcher.ReportPeriod> values = new ArrayList<>();
        if (dueDate.isAfter(today)) return List.of();
        LocalDate latestMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (schedule.weeklyEnabled() && !latestMonday.isBefore(dueDate)) {
            LocalDate previous = latestMonday.minusWeeks(1);
            WeekFields fields = WeekFields.ISO;
            values.add(
                    new AiScheduledReportDispatcher.ReportPeriod(
                            "WEEKLY",
                            String.format(
                                    "%04d-W%02d",
                                    previous.get(fields.weekBasedYear()),
                                    previous.get(fields.weekOfWeekBasedYear()))));
        }
        LocalDate latestMonthStart = today.withDayOfMonth(1);
        if (schedule.monthlyEnabled() && !latestMonthStart.isBefore(dueDate)) {
            values.add(
                    new AiScheduledReportDispatcher.ReportPeriod(
                            "MONTHLY",
                            java.time.YearMonth.from(latestMonthStart.minusMonths(1)).toString()));
        }
        LocalDate latestYearStart = today.withDayOfYear(1);
        if (schedule.annualEnabled() && !latestYearStart.isBefore(dueDate)) {
            values.add(
                    new AiScheduledReportDispatcher.ReportPeriod(
                            "ANNUAL", String.valueOf(latestYearStart.getYear() - 1)));
        }
        return List.copyOf(values);
    }

    private LocalDateTime nextRun(ZonedDateTime now) {
        ZonedDateTime candidate =
                now.toLocalDate().atTime(6, 5).atZone(ZONE).withSecond(0).withNano(0);
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);
        return candidate.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
