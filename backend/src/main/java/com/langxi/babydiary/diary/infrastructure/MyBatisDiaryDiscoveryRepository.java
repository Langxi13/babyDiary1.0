package com.langxi.babydiary.diary.infrastructure;

import com.langxi.babydiary.diary.application.DiaryDiscoveryRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDiaryDiscoveryRepository implements DiaryDiscoveryRepository {
    private final DiaryDiscoveryMapper mapper;

    public MyBatisDiaryDiscoveryRepository(DiaryDiscoveryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SearchRow> searchFullText(long spaceId, long accountId, String query, int limit) {
        return mapper.searchFullText(spaceId, accountId, query, limit).stream()
                .map(this::searchRow)
                .toList();
    }

    @Override
    public List<SearchRow> searchLike(long spaceId, long accountId, String query, int limit) {
        return mapper.searchLike(spaceId, accountId, query, limit).stream()
                .map(this::searchRow)
                .toList();
    }

    @Override
    public List<DayCount> findDays(long spaceId, long accountId, LocalDate start, LocalDate end) {
        return mapper.findDays(spaceId, accountId, start, end).stream()
                .map(row -> new DayCount(row.day(), row.itemCount()))
                .toList();
    }

    @Override
    public List<MoodCount> findMoods(long spaceId, long accountId, LocalDate start, LocalDate end) {
        return mapper.findMoods(spaceId, accountId, start, end).stream()
                .map(row -> new MoodCount(row.moodKey(), row.itemCount()))
                .toList();
    }

    @Override
    public List<MonthCount> findMonths(
            long spaceId, long accountId, LocalDate start, LocalDate end) {
        return mapper.findMonths(spaceId, accountId, start, end).stream()
                .map(row -> new MonthCount(row.month(), row.itemCount()))
                .toList();
    }

    @Override
    public int countPhotos(long spaceId, long accountId, LocalDate start, LocalDate end) {
        return mapper.countPhotos(spaceId, accountId, start, end);
    }

    private SearchRow searchRow(DiaryDiscoveryMapper.SearchRow row) {
        return new SearchRow(
                row.getPublicId(),
                row.getTitle(),
                row.getSnippet(),
                row.getDiaryDate(),
                row.getScore());
    }
}
