package com.langxi.babydiary.diary.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DiaryReadRepository {
    List<CalendarRow> findCalendar(long spaceId, long accountId, LocalDate start, LocalDate end);

    List<MonthCount> findMonthCounts(
            long spaceId, long accountId, String mood, UUID tagId, boolean elevated);

    List<WeekCount> findWeekCounts(
            long spaceId,
            long accountId,
            LocalDate start,
            LocalDate end,
            String mood,
            UUID tagId,
            boolean elevated);

    record CalendarRow(
            UUID diaryId,
            LocalDate date,
            String title,
            String mood,
            int mediaCount,
            boolean locked) {}

    record MonthCount(int year, int month, long count, long mediaCount) {}

    record WeekCount(LocalDate weekStart, long count) {}
}
