package com.langxi.babydiary.ai.application;

import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiReportService {
    private static final int MAX_INPUT_CHARS = 30_000;
    private static final int MAX_DIARY_CHARS = 1_200;
    private final SpaceAccess spaces;
    private final AiConfigService configs;
    private final AiClient client;
    private final AiReportRepository reports;
    private final AiReportPersistenceService persistence;

    public AiReportService(
            SpaceAccess spaces,
            AiConfigService configs,
            AiClient client,
            AiReportRepository reports,
            AiReportPersistenceService persistence) {
        this.spaces = spaces;
        this.configs = configs;
        this.client = client;
        this.reports = reports;
        this.persistence = persistence;
    }

    public ReportPage list(UUID spaceId, long accountId, String periodType, int page, int size) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        String normalizedType = normalizeListType(periodType);
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.max(1, Math.min(size, 50));
        long total = reports.count(space.internalId(), accountId, normalizedType);
        long rawOffset = (long) normalizedPage * normalizedSize;
        List<ReportView> content =
                rawOffset >= total || rawOffset > Integer.MAX_VALUE
                        ? List.of()
                        : reports
                                .findPage(
                                        space.internalId(),
                                        accountId,
                                        normalizedType,
                                        (int) rawOffset,
                                        normalizedSize)
                                .stream()
                                .map(this::toView)
                                .toList();
        return new ReportPage(content, normalizedPage, normalizedSize, total);
    }

    public ReportView detail(UUID spaceId, UUID reportId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return reports.findByPublicId(space.internalId(), accountId, reportId)
                .map(this::toView)
                .orElseThrow(() -> ApiException.notFound("AI_REPORT_NOT_FOUND", "AI 报告不存在或无权访问"));
    }

    @Transactional
    public void delete(UUID spaceId, UUID reportId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        if (!reports.delete(space.internalId(), accountId, reportId)) {
            throw ApiException.notFound("AI_REPORT_NOT_FOUND", "AI 报告不存在或无权访问");
        }
    }

    public ReportView generate(UUID spaceId, long accountId, String type, String period) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        Period value = resolve(type, period);
        List<AiReportRepository.DiaryInput> diaries =
                reports.findDiaries(space.internalId(), accountId, value.start(), value.end());
        if (diaries.isEmpty())
            throw ApiException.badRequest("AI_REPORT_NO_DIARIES", "该周期没有可用于报告的日记");
        AiRuntimeConfig config = configs.runtime();
        String markdown =
                client.generate(
                        config,
                        List.of(
                                new AiClient.Message("system", systemPrompt()),
                                new AiClient.Message("user", userPrompt(value, diaries))));
        UUID publicId = UUID.randomUUID();
        String title = value.label() + " " + reportName(value.type());
        return toView(
                persistence.save(
                        new AiReportRepository.NewReport(
                                publicId,
                                space.internalId(),
                                accountId,
                                value.type(),
                                value.start(),
                                value.end(),
                                title,
                                markdown,
                                diaries.size(),
                                config.model()),
                        diaries));
    }

    public java.util.Optional<ReportView> findExisting(
            UUID spaceId, long accountId, String type, String period) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        Period value = resolve(type, period);
        return reports.findByPeriod(
                        space.internalId(), accountId, value.type(), value.start(), value.end())
                .map(this::toView);
    }

    private Period resolve(String type, String period) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        String value = period == null ? "" : period.trim();
        try {
            if ("MONTHLY".equals(normalized)) {
                YearMonth month = YearMonth.parse(value);
                return new Period(normalized, value, month.atDay(1), month.atEndOfMonth());
            }
            if ("ANNUAL".equals(normalized) && value.matches("\\d{4}")) {
                int year = Integer.parseInt(value);
                return new Period(
                        normalized, value, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
            }
            if ("WEEKLY".equals(normalized) && value.matches("\\d{4}-W\\d{2}")) {
                int year = Integer.parseInt(value.substring(0, 4));
                int week = Integer.parseInt(value.substring(6));
                LocalDate monday =
                        LocalDate.of(year, 1, 4)
                                .with(WeekFields.ISO.weekBasedYear(), year)
                                .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                                .with(DayOfWeek.MONDAY);
                return new Period(normalized, value, monday, monday.plusDays(6));
            }
        } catch (RuntimeException ignored) {
        }
        throw ApiException.badRequest(
                "AI_REPORT_PERIOD_INVALID", "报告周期格式无效，请使用 yyyy、yyyy-MM 或 yyyy-Www");
    }

    private String systemPrompt() {
        return "你是 Baby Diary 的第三方回顾整理者。请站在旁观者角度，根据提供的事实进行总结，面向日记主人使用第二人称，可以使用“你”或“你们”，例如“在这个月中，你们一起走过了...”和“你完成了...”。禁止用第一人称“我/我们”代入日记主人，禁止编造事实。输出结构清晰、可读的 Markdown。";
    }

    private String userPrompt(Period period, List<AiReportRepository.DiaryInput> diaries) {
        StringBuilder prompt =
                new StringBuilder("请生成一份")
                        .append(reportName(period.type()))
                        .append("，周期：")
                        .append(period.start())
                        .append(" 至 ")
                        .append(period.end())
                        .append("。要求包含：# 标题、## 本期回顾、## 重要瞬间、## 情绪与陪伴、## 温柔总结。\n\n日记：\n");
        for (AiReportRepository.DiaryInput diary : diaries) {
            String item =
                    "- 日期："
                            + diary.date()
                            + "\n  标题："
                            + safe(diary.title())
                            + "\n  心情："
                            + safe(diary.mood())
                            + "\n  内容："
                            + truncate(safe(diary.contentText()), MAX_DIARY_CHARS)
                            + "\n";
            if (prompt.length() + item.length() > MAX_INPUT_CHARS) break;
            prompt.append(item);
        }
        return prompt.toString();
    }

    private String reportName(String type) {
        return switch (type) {
            case "WEEKLY" -> "周报";
            case "MONTHLY" -> "月报";
            case "ANNUAL" -> "年度回顾";
            default -> "回顾报告";
        };
    }

    private String normalizeListType(String type) {
        if (type == null || type.isBlank()) return null;
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!List.of("WEEKLY", "MONTHLY", "ANNUAL").contains(normalized)) {
            throw ApiException.badRequest("AI_REPORT_TYPE_INVALID", "报告类型无效");
        }
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private ReportView toView(AiReportRepository.Report report) {
        return new ReportView(
                report.id(),
                report.spaceId(),
                report.periodType(),
                report.start(),
                report.end(),
                report.title(),
                report.markdown(),
                report.diaryCount(),
                report.model(),
                report.createdAt());
    }

    private record Period(String type, String label, LocalDate start, LocalDate end) {}

    public record ReportPage(
            List<ReportView> content, int pageNumber, int pageSize, long totalElements) {
        public long totalPages() {
            return totalElements == 0 ? 0 : (totalElements + pageSize - 1) / pageSize;
        }
    }

    public record ReportView(
            UUID id,
            UUID spaceId,
            String periodType,
            LocalDate start,
            LocalDate end,
            String title,
            String markdown,
            int diaryCount,
            String model,
            java.time.LocalDateTime createdAt) {}
}
