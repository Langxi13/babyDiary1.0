package com.langxi.babydiary.diary.infrastructure;

import com.langxi.babydiary.diary.application.DiaryReadRepository;
import com.langxi.babydiary.platform.application.BinaryUuid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDiaryReadRepository implements DiaryReadRepository {
    private final DiaryReadMapper mapper;

    public MyBatisDiaryReadRepository(DiaryReadMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<CalendarRow> findCalendar(
            long spaceId, long accountId, LocalDate start, LocalDate end) {
        return mapper.findCalendar(spaceId, accountId, start, end).stream()
                .map(
                        row ->
                                new CalendarRow(
                                        BinaryUuid.fromBytes(row.publicId()),
                                        row.diaryDate(),
                                        row.title(),
                                        row.moodKey(),
                                        row.mediaCount(),
                                        row.locked()))
                .toList();
    }

    @Override
    public List<MonthCount> findMonthCounts(
            long spaceId, long accountId, String mood, java.util.UUID tagId, boolean elevated) {
        return mapper
                .findMonthCounts(
                        spaceId,
                        accountId,
                        mood,
                        tagId == null ? null : BinaryUuid.toBytes(tagId),
                        elevated)
                .stream()
                .map(
                        row ->
                                new MonthCount(
                                        row.diaryYear(),
                                        row.diaryMonth(),
                                        row.diaryCount(),
                                        row.mediaCount()))
                .toList();
    }

    @Override
    public List<WeekCount> findWeekCounts(
            long spaceId,
            long accountId,
            LocalDate start,
            LocalDate end,
            String mood,
            java.util.UUID tagId,
            boolean elevated) {
        return mapper
                .findWeekCounts(
                        spaceId,
                        accountId,
                        start,
                        end,
                        mood,
                        tagId == null ? null : BinaryUuid.toBytes(tagId),
                        elevated)
                .stream()
                .map(row -> new WeekCount(row.weekStart(), row.diaryCount()))
                .toList();
    }
}
