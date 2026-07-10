package com.langxi.babydiary.service;

import org.junit.jupiter.api.Test;

import java.sql.Date;

import static org.assertj.core.api.Assertions.assertThat;

class AiPeriodResolverTest {

    private final AiPeriodResolver resolver = new AiPeriodResolver();

    @Test
    void resolvesIsoWeekToMondayAndSunday() {
        AiReportPeriod period = resolver.resolve("WEEKLY", "2026-W27");

        assertThat(period.getStartDate()).isEqualTo(Date.valueOf("2026-06-29"));
        assertThat(period.getEndDate()).isEqualTo(Date.valueOf("2026-07-05"));
        assertThat(period.getLabel()).isEqualTo("2026-W27");
    }

    @Test
    void resolvesMonthToNaturalMonthRange() {
        AiReportPeriod period = resolver.resolve("MONTHLY", "2026-07");

        assertThat(period.getStartDate()).isEqualTo(Date.valueOf("2026-07-01"));
        assertThat(period.getEndDate()).isEqualTo(Date.valueOf("2026-07-31"));
        assertThat(period.getLabel()).isEqualTo("2026-07");
    }
}
