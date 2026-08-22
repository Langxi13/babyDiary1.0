package com.langxi.babydiary.diary.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.ReadCache;
import com.langxi.babydiary.platform.application.ReadCacheInvalidator;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DiaryReadService {
    private static final TypeReference<CalendarMonth> CALENDAR = new TypeReference<>() {};
    private static final TypeReference<TimelineIndex> TIMELINE = new TypeReference<>() {};
    private static final TypeReference<List<WeekSummary>> WEEKS = new TypeReference<>() {};
    private final SpaceAccess spaces;
    private final DiaryReadRepository reads;
    private final ReadCache cache;

    public DiaryReadService(SpaceAccess spaces, DiaryReadRepository reads, ReadCache cache) {
        this.spaces = spaces;
        this.reads = reads;
        this.cache = cache;
    }

    public CalendarMonth calendar(UUID spaceId, long accountId, YearMonth month, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        if (month == null) throw ApiException.badRequest("MONTH_REQUIRED", "请选择月份");
        if (!elevated) {
            return cache.get(
                    ReadCacheInvalidator.DIARY_AGGREGATES,
                    spaceId,
                    accountId,
                    "calendar:" + month,
                    Duration.ofMinutes(3),
                    CALENDAR,
                    () -> calendar(space.internalId(), accountId, month, false));
        }
        return calendar(space.internalId(), accountId, month, true);
    }

    private CalendarMonth calendar(
            long spaceId, long accountId, YearMonth month, boolean elevated) {
        Map<LocalDate, List<DiaryReadRepository.CalendarRow>> grouped = new LinkedHashMap<>();
        reads.findCalendar(spaceId, accountId, month.atDay(1), month.atEndOfMonth())
                .forEach(
                        row ->
                                grouped.computeIfAbsent(row.date(), ignored -> new ArrayList<>())
                                        .add(row));
        List<CalendarDay> days =
                grouped.entrySet().stream()
                        .map(
                                entry ->
                                        new CalendarDay(
                                                entry.getKey(),
                                                entry.getValue().size(),
                                                entry.getValue().stream()
                                                        .limit(3)
                                                        .map(
                                                                row ->
                                                                        new CalendarEntry(
                                                                                row.diaryId(),
                                                                                row.locked()
                                                                                                && !elevated
                                                                                        ? null
                                                                                        : ellipsis(
                                                                                                row
                                                                                                        .title(),
                                                                                                18),
                                                                                row.locked()
                                                                                                && !elevated
                                                                                        ? null
                                                                                        : row
                                                                                                .mood(),
                                                                                row.locked()
                                                                                                && !elevated
                                                                                        ? 0
                                                                                        : row
                                                                                                .mediaCount(),
                                                                                row.locked()))
                                                        .toList()))
                        .toList();
        return new CalendarMonth(month, days);
    }

    public TimelineIndex timeline(
            UUID spaceId, long accountId, String mood, UUID tagId, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        String normalizedMood = mood == null || mood.isBlank() ? null : mood.trim();
        if (elevated) {
            return timeline(space.internalId(), accountId, normalizedMood, tagId, true);
        }
        return cache.get(
                ReadCacheInvalidator.DIARY_AGGREGATES,
                spaceId,
                accountId,
                "timeline:mood=" + normalizedMood + ":tag=" + tagId,
                Duration.ofMinutes(3),
                TIMELINE,
                () -> timeline(space.internalId(), accountId, normalizedMood, tagId, false));
    }

    private TimelineIndex timeline(
            long spaceId, long accountId, String mood, UUID tagId, boolean elevated) {
        Map<Integer, List<MonthSummary>> months = new LinkedHashMap<>();
        Map<Integer, Long> yearCounts = new LinkedHashMap<>();
        Map<Integer, Long> yearMediaCounts = new LinkedHashMap<>();
        for (DiaryReadRepository.MonthCount row :
                reads.findMonthCounts(spaceId, accountId, mood, tagId, elevated)) {
            months.computeIfAbsent(row.year(), ignored -> new ArrayList<>())
                    .add(
                            new MonthSummary(
                                    YearMonth.of(row.year(), row.month()),
                                    row.count(),
                                    row.mediaCount()));
            yearCounts.merge(row.year(), row.count(), Long::sum);
            yearMediaCounts.merge(row.year(), row.mediaCount(), Long::sum);
        }
        return new TimelineIndex(
                yearCounts.entrySet().stream()
                        .map(
                                entry ->
                                        new YearSummary(
                                                entry.getKey(),
                                                entry.getValue(),
                                                yearMediaCounts.getOrDefault(entry.getKey(), 0L),
                                                List.copyOf(months.get(entry.getKey()))))
                        .toList());
    }

    public List<WeekSummary> weeks(
            UUID spaceId,
            long accountId,
            YearMonth month,
            String mood,
            UUID tagId,
            boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        if (month == null) throw ApiException.badRequest("MONTH_REQUIRED", "请选择月份");
        String normalizedMood = mood == null || mood.isBlank() ? null : mood.trim();
        if (elevated) {
            return weekRows(space, accountId, month, normalizedMood, tagId, true);
        }
        return cache.get(
                ReadCacheInvalidator.DIARY_AGGREGATES,
                spaceId,
                accountId,
                "weeks:" + month + ":mood=" + normalizedMood + ":tag=" + tagId,
                Duration.ofMinutes(3),
                WEEKS,
                () -> weekRows(space, accountId, month, normalizedMood, tagId, false));
    }

    private List<WeekSummary> weekRows(
            SpaceAccess.SpaceContext space,
            long accountId,
            YearMonth month,
            String mood,
            UUID tagId,
            boolean elevated) {
        return reads
                .findWeekCounts(
                        space.internalId(),
                        accountId,
                        month.atDay(1),
                        month.atEndOfMonth(),
                        mood,
                        tagId,
                        elevated)
                .stream()
                .map(
                        row ->
                                new WeekSummary(
                                        row.weekStart(), row.weekStart().plusDays(6), row.count()))
                .toList();
    }

    private String ellipsis(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max) + "...";
    }

    public record CalendarMonth(YearMonth month, List<CalendarDay> days) {}

    public record CalendarDay(LocalDate date, int count, List<CalendarEntry> entries) {}

    public record CalendarEntry(
            UUID diaryId, String title, String mood, int mediaCount, boolean locked) {}

    public record TimelineIndex(List<YearSummary> years) {}

    public record YearSummary(int year, long count, long mediaCount, List<MonthSummary> months) {}

    public record MonthSummary(YearMonth month, long count, long mediaCount) {}

    public record WeekSummary(LocalDate start, LocalDate end, long count) {}
}
