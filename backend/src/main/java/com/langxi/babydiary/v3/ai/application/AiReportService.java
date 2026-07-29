package com.langxi.babydiary.v3.ai.application;

import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.space.application.SpaceAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AiReportService {
    private static final int MAX_INPUT_CHARS = 30_000;
    private static final int MAX_DIARY_CHARS = 1_200;
    private final SpaceAccess spaces;
    private final AiConfigService configs;
    private final V3AiClient client;
    private final AiReportRepository reports;

    public AiReportService(SpaceAccess spaces, AiConfigService configs, V3AiClient client, AiReportRepository reports) {
        this.spaces = spaces;
        this.configs = configs;
        this.client = client;
        this.reports = reports;
    }

    public List<AiReportRepository.Report> list(UUID spaceId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return reports.findForCreator(space.internalId(), accountId);
    }

    public AiReportRepository.Report detail(UUID spaceId, UUID reportId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return reports.findByPublicId(space.internalId(), accountId, reportId)
                .orElseThrow(() -> V3Exception.notFound("AI_REPORT_NOT_FOUND", "AI 报告不存在或无权访问"));
    }

    @Transactional
    public void delete(UUID spaceId, UUID reportId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        if (!reports.delete(space.internalId(), accountId, reportId)) {
            throw V3Exception.notFound("AI_REPORT_NOT_FOUND", "AI 报告不存在或无权访问");
        }
    }

    @Transactional
    public AiReportRepository.Report generate(UUID spaceId, long accountId, String type, String period) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        Period value = resolve(type, period);
        List<AiReportRepository.DiaryInput> diaries = reports.findDiaries(space.internalId(), accountId, value.start(), value.end());
        if (diaries.isEmpty()) throw V3Exception.badRequest("AI_REPORT_NO_DIARIES", "该周期没有可用于报告的日记");
        String markdown = client.generate(configs.runtime(), List.of(
                new V3AiClient.Message("system", systemPrompt()),
                new V3AiClient.Message("user", userPrompt(value, diaries))));
        AiRuntimeConfig config = configs.runtime();
        UUID publicId = UUID.randomUUID();
        String title = value.label() + ("WEEKLY".equals(value.type()) ? " 周报" : " 月报");
        long reportId = reports.insert(new AiReportRepository.NewReport(publicId, space.internalId(), accountId,
                value.type(), value.start(), value.end(), title, markdown, diaries.size(), config.model()));
        for (AiReportRepository.DiaryInput diary : diaries) reports.insertDiary(space.internalId(), reportId, diary.internalId());
        return detail(spaceId, publicId, accountId);
    }

    private Period resolve(String type, String period) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        String value = period == null ? "" : period.trim();
        try {
            if ("MONTHLY".equals(normalized)) {
                YearMonth month = YearMonth.parse(value);
                return new Period(normalized, value, month.atDay(1), month.atEndOfMonth());
            }
            if ("WEEKLY".equals(normalized) && value.matches("\\d{4}-W\\d{2}")) {
                int year = Integer.parseInt(value.substring(0, 4));
                int week = Integer.parseInt(value.substring(6));
                LocalDate monday = LocalDate.of(year, 1, 4).with(WeekFields.ISO.weekBasedYear(), year)
                        .with(WeekFields.ISO.weekOfWeekBasedYear(), week).with(DayOfWeek.MONDAY);
                return new Period(normalized, value, monday, monday.plusDays(6));
            }
        } catch (RuntimeException ignored) {
        }
        throw V3Exception.badRequest("AI_REPORT_PERIOD_INVALID", "报告周期格式无效，请使用 yyyy-MM 或 yyyy-Www");
    }

    private String systemPrompt() {
        return "你是 Baby Diary 的第三方回顾整理者。请站在旁观者角度，根据提供的事实进行总结，面向日记主人使用第二人称，可以使用“你”或“你们”，例如“在这个月中，你们一起走过了...”和“你完成了...”。禁止用第一人称“我/我们”代入日记主人，禁止编造事实。输出结构清晰、可读的 Markdown。";
    }

    private String userPrompt(Period period, List<AiReportRepository.DiaryInput> diaries) {
        StringBuilder prompt = new StringBuilder("请生成一份").append("WEEKLY".equals(period.type()) ? "周报" : "月报")
                .append("，周期：").append(period.start()).append(" 至 ").append(period.end())
                .append("。要求包含：# 标题、## 本期回顾、## 重要瞬间、## 情绪与陪伴、## 温柔总结。\n\n日记：\n");
        for (AiReportRepository.DiaryInput diary : diaries) {
            String item = "- 日期：" + diary.date() + "\n  标题：" + safe(diary.title())
                    + "\n  心情：" + safe(diary.mood()) + "\n  内容：" + truncate(safe(diary.contentText()), MAX_DIARY_CHARS) + "\n";
            if (prompt.length() + item.length() > MAX_INPUT_CHARS) break;
            prompt.append(item);
        }
        return prompt.toString();
    }

    private String safe(String value) { return value == null ? "" : value; }
    private String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max) + "..."; }

    private record Period(String type, String label, LocalDate start, LocalDate end) {
    }
}
