package com.langxi.babydiary.diary.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.platform.application.ReadCache;
import com.langxi.babydiary.platform.application.ReadCacheInvalidator;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DiaryDiscoveryService {
    private static final TypeReference<YearInsight> YEAR_INSIGHT = new TypeReference<>() {};
    private final SpaceAccess spaces;
    private final DiaryDiscoveryRepository mapper;
    private final ReadCache cache;

    public DiaryDiscoveryService(
            SpaceAccess spaces, DiaryDiscoveryRepository mapper, ReadCache cache) {
        this.spaces = spaces;
        this.mapper = mapper;
        this.cache = cache;
    }

    public SearchResponse search(UUID spaceId, long accountId, String query, int limit) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        String value = query == null ? "" : query.trim();
        if (value.isBlank() || value.length() > 100)
            throw ApiException.badRequest("SEARCH_QUERY_INVALID", "搜索内容长度应为1到100个字符");
        int size = Math.max(1, Math.min(limit, 100));
        List<DiaryDiscoveryRepository.SearchRow> rows =
                value.codePointCount(0, value.length()) < 2
                        ? mapper.searchLike(space.internalId(), accountId, value, size)
                        : mapper.searchFullText(space.internalId(), accountId, value, size);
        return new SearchResponse(
                value,
                false,
                rows.stream()
                        .map(
                                row ->
                                        new SearchResult(
                                                "DIARY",
                                                BinaryUuid.fromBytes(row.publicId()),
                                                row.title(),
                                                row.snippet(),
                                                row.diaryDate(),
                                                row.score()))
                        .toList());
    }

    public YearInsight yearly(UUID spaceId, long accountId, int year) {
        if (year < 1900 || year > LocalDate.now().getYear() + 1)
            throw ApiException.badRequest("INSIGHT_YEAR_INVALID", "统计年份无效");
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return cache.get(
                ReadCacheInvalidator.DIARY_AGGREGATES,
                spaceId,
                accountId,
                "year-insight:" + year,
                Duration.ofMinutes(3),
                YEAR_INSIGHT,
                () -> yearly(space.internalId(), accountId, year));
    }

    private YearInsight yearly(long spaceId, long accountId, int year) {
        LocalDate start = LocalDate.of(year, 1, 1), end = LocalDate.of(year, 12, 31);
        List<DiaryDiscoveryRepository.DayCount> days =
                mapper.findDays(spaceId, accountId, start, end);
        Streak streak = streak(days);
        return new YearInsight(
                year,
                days.stream().mapToLong(DiaryDiscoveryRepository.DayCount::itemCount).sum(),
                days.size(),
                streak.current,
                streak.longest,
                mapper.countPhotos(spaceId, accountId, start, end),
                days.stream().map(row -> new Day(row.day(), row.itemCount())).toList(),
                mapper.findMoods(spaceId, accountId, start, end).stream()
                        .map(row -> new Mood(row.moodKey(), row.itemCount()))
                        .toList(),
                mapper.findMonths(spaceId, accountId, start, end).stream()
                        .map(row -> new Month(row.month(), row.itemCount()))
                        .toList());
    }

    private Streak streak(List<DiaryDiscoveryRepository.DayCount> rows) {
        Set<LocalDate> active = new HashSet<>();
        rows.forEach(row -> active.add(row.day()));
        int longest = 0, run = 0;
        LocalDate previous = null;
        for (LocalDate date : active.stream().sorted().toList()) {
            run = previous != null && date.equals(previous.plusDays(1)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = date;
        }
        LocalDate cursor = LocalDate.now();
        if (!active.contains(cursor)) cursor = cursor.minusDays(1);
        int current = 0;
        while (active.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }
        return new Streak(current, longest);
    }

    public record SearchResponse(
            String query, boolean semanticEnabled, List<SearchResult> results) {}

    public record SearchResult(
            String entityType,
            UUID entityId,
            String title,
            String snippet,
            LocalDate date,
            double score) {}

    public record YearInsight(
            int year,
            long diaryCount,
            int activeDays,
            int currentStreak,
            int longestStreak,
            int photoCount,
            List<Day> heatmap,
            List<Mood> moods,
            List<Month> months) {}

    public record Day(LocalDate date, long count) {}

    public record Mood(String mood, long count) {}

    public record Month(String month, long count) {}

    private record Streak(int current, int longest) {}
}
