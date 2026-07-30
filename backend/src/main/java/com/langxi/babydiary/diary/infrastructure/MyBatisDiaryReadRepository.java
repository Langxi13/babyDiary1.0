package com.langxi.babydiary.diary.infrastructure;

import com.langxi.babydiary.diary.application.DiaryReadRepository;
import com.langxi.babydiary.platform.application.BinaryUuid;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class MyBatisDiaryReadRepository implements DiaryReadRepository {
    private final DiaryReadMapper mapper;

    public MyBatisDiaryReadRepository(DiaryReadMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<CalendarRow> findCalendar(long spaceId, long accountId, LocalDate start, LocalDate end) {
        return mapper.findCalendar(spaceId, accountId, start, end).stream().map(row -> new CalendarRow(
                BinaryUuid.fromBytes(row.publicId()), row.diaryDate(), row.title(), row.moodKey(), row.mediaCount(),row.locked())).toList();
    }

    @Override
    public List<MonthCount> findMonthCounts(long spaceId, long accountId) {
        return mapper.findMonthCounts(spaceId, accountId).stream()
                .map(row -> new MonthCount(row.diaryYear(), row.diaryMonth(), row.diaryCount())).toList();
    }

    @Override
    public List<WeekCount> findWeekCounts(long spaceId, long accountId, LocalDate start, LocalDate end) {
        return mapper.findWeekCounts(spaceId, accountId, start, end).stream()
                .map(row -> new WeekCount(row.weekStart(), row.diaryCount())).toList();
    }
}
