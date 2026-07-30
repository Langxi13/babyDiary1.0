package com.langxi.babydiary.diary.application;

import java.time.LocalDate;
import java.util.List;

public interface DiaryDiscoveryRepository {
    List<SearchRow> searchFullText(long spaceId, long accountId, String query, int limit);

    List<SearchRow> searchLike(long spaceId, long accountId, String query, int limit);

    List<DayCount> findDays(long spaceId, long accountId, LocalDate start, LocalDate end);

    List<MoodCount> findMoods(long spaceId, long accountId, LocalDate start, LocalDate end);

    List<MonthCount> findMonths(long spaceId, long accountId, LocalDate start, LocalDate end);

    int countPhotos(long spaceId, long accountId, LocalDate start, LocalDate end);

    record SearchRow(
            byte[] publicId, String title, String snippet, LocalDate diaryDate, double score) {}

    record DayCount(LocalDate day, long itemCount) {}

    record MoodCount(String moodKey, long itemCount) {}

    record MonthCount(String month, long itemCount) {}
}
