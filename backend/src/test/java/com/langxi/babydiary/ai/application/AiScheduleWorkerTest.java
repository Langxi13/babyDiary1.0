package com.langxi.babydiary.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiScheduleWorkerTest {
    private final AiScheduleWorker worker = new AiScheduleWorker(null, null, false);

    @Test
    void catchesUpLatestPeriodBoundariesAfterDowntime() {
        AiScheduleRepository.DueSchedule schedule = schedule(true, true, true);

        var periods = worker.periods(schedule, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 4));

        assertThat(periods)
                .containsExactly(
                        new AiScheduledReportDispatcher.ReportPeriod("WEEKLY", "2026-W05"),
                        new AiScheduledReportDispatcher.ReportPeriod("MONTHLY", "2026-01"),
                        new AiScheduledReportDispatcher.ReportPeriod("ANNUAL", "2025"));
    }

    @Test
    void doesNotGeneratePeriodsThatPredateScheduleActivation() {
        AiScheduleRepository.DueSchedule schedule = schedule(true, true, true);

        var periods = worker.periods(schedule, LocalDate.of(2026, 2, 3), LocalDate.of(2026, 2, 4));

        assertThat(periods).isEmpty();
    }

    private AiScheduleRepository.DueSchedule schedule(
            boolean weekly, boolean monthly, boolean annual) {
        return new AiScheduleRepository.DueSchedule(
                1L,
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                2L,
                weekly,
                monthly,
                annual,
                LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}
