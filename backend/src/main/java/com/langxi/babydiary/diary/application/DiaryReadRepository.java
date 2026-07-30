package com.langxi.babydiary.diary.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DiaryReadRepository {
    List<CalendarRow> findCalendar(long spaceId, long accountId, LocalDate start, LocalDate end);

    List<MonthCount> findMonthCounts(long spaceId, long accountId);

    List<WeekCount> findWeekCounts(long spaceId, long accountId, LocalDate start, LocalDate end);

    record CalendarRow(
            UUID diaryId,
            LocalDate date,
            String title,
            String mood,
            int mediaCount,
            boolean locked) {}

    record MonthCount(int year, int month, long count) {}

    record WeekCount(LocalDate weekStart, long count) {}
}
