package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Pagination;
import com.langxi.babydiary.dto.AiReportGenerateDTO;
import com.langxi.babydiary.entity.AiReport;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.Tag;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AiReportMapper;
import com.langxi.babydiary.mapper.DiaryMapper;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiReportService {

    private static final int MAX_CONTENT_PER_DIARY = 1000;
    private static final int MAX_INPUT_CHARS = 30000;

    @Autowired
    private AiConfigService aiConfigService;

    @Autowired
    private AiPeriodResolver aiPeriodResolver;

    @Autowired
    private OpenAiCompatibleClient aiClient;

    @Autowired
    private DiaryMapper diaryMapper;

    @Autowired
    private TagService tagService;

    @Autowired
    private AiReportMapper aiReportMapper;

    @Transactional
    public AiReport generate(Integer userId, AiReportGenerateDTO dto) {
        AiRuntimeConfig config = aiConfigService.getRuntimeConfig();
        AiReportPeriod period = aiPeriodResolver.resolve(dto.getType(), dto.getPeriod());
        List<Diary> diaries = diaryMapper.findDiariesForReport(userId, period.getStartDate(), period.getEndDate());
        if (diaries.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_CONFIG_INVALID, "该周期没有日记，无法生成报告");
        }
        enrichTags(diaries);

        String markdown = aiClient.generate(config, Arrays.asList(
                new AiChatMessage("system", systemPrompt()),
                new AiChatMessage("user", userPrompt(period, diaries))
        ));

        AiReport report = new AiReport();
        report.setUserId(userId);
        report.setType(period.getType());
        report.setPeriod(period.getLabel());
        report.setPeriodStart(period.getStartDate());
        report.setPeriodEnd(period.getEndDate());
        report.setTitle(reportTitle(period));
        report.setContentMarkdown(markdown);
        report.setDiaryCount(diaries.size());
        report.setModel(config.getModel());
        aiReportMapper.insert(report);
        return report;
    }

    public PageResult<AiReport> findReports(Integer userId, String type, int page, int size) {
        int normalizedPage = Pagination.normalizePage(page);
        int normalizedSize = Pagination.normalizeSize(size);
        String normalizedType = normalizeType(type);
        int total = aiReportMapper.count(userId, normalizedType);
        List<AiReport> reports = aiReportMapper.findPage(userId, normalizedType, normalizedSize, (long) normalizedPage * normalizedSize);
        return new PageResult<>(reports, normalizedPage, normalizedSize, (long) total);
    }

    public AiReport findById(Integer userId, Integer reportId) {
        AiReport report = aiReportMapper.findById(userId, reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.AI_REPORT_NOT_FOUND);
        }
        return report;
    }

    public void delete(Integer userId, Integer reportId) {
        aiReportMapper.delete(userId, reportId);
    }

    private void enrichTags(List<Diary> diaries) {
        List<Integer> diaryIds = diaries.stream().map(Diary::getDiaryId).collect(Collectors.toList());
        Map<Integer, List<Tag>> tagsByDiaryId = tagService.findTagsByDiaryIds(diaryIds);
        for (Diary diary : diaries) {
            diary.setTagList(tagsByDiaryId.get(diary.getDiaryId()));
        }
    }

    private String systemPrompt() {
        return "你是 Baby Diary 的温暖回忆整理助手。你以旁观整理者的身份写周报或月报，但要使用第二人称面向用户总结，允许用“你/你们”称呼日记主人，例如“在这个月中，你们一起走过了...”、“你完成了...”。不要用“我”或“我们”代入日记主人。你只能基于用户提供的日记事实写作，不要编造未出现的人物、事件或地点。输出 Markdown。";
    }

    private String userPrompt(AiReportPeriod period, List<Diary> diaries) {
        StringBuilder builder = new StringBuilder();
        builder.append("请生成一份").append("WEEKLY".equals(period.getType()) ? "周报" : "月报")
                .append("，周期：").append(period.getStartDate()).append(" 至 ").append(period.getEndDate()).append("。\n\n")
                .append("写作口吻：以旁观整理者身份、第二人称口吻总结，允许使用“你/你们”，例如“在这个月中，你们一起走过了...”、“你完成了...”。不要使用“我”或“我们”代入日记主人。\n\n")
                .append("要求结构：# 标题、## 本期回顾、## 重要瞬间、## 情绪与陪伴、## 温柔总结。\n\n")
                .append("日记内容：\n");
        for (Diary diary : diaries) {
            String item = formatDiary(diary);
            if (builder.length() + item.length() > MAX_INPUT_CHARS) {
                builder.append("\n[后续日记因长度限制已省略]\n");
                break;
            }
            builder.append(item);
        }
        return builder.toString();
    }

    private String formatDiary(Diary diary) {
        String tags = "";
        if (diary.getTagList() != null && !diary.getTagList().isEmpty()) {
            tags = diary.getTagList().stream().map(Tag::getName).collect(Collectors.joining("、"));
        }
        return "- 日期：" + diary.getDate()
                + "\n  标题：" + nullToEmpty(diary.getTitle())
                + "\n  心情：" + nullToEmpty(diary.getMoodKey())
                + "\n  标签：" + tags
                + "\n  内容：" + truncate(toPlainText(diary.getContent()), MAX_CONTENT_PER_DIARY)
                + "\n";
    }

    private String reportTitle(AiReportPeriod period) {
        return period.getLabel() + ("WEEKLY".equals(period.getType()) ? " 周报" : " 月报");
    }

    private String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit) + "...";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String toPlainText(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }
        return Jsoup.parse(html).text();
    }

    private String normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        String normalized = type.trim().toUpperCase();
        if (!"WEEKLY".equals(normalized) && !"MONTHLY".equals(normalized)) {
            throw new IllegalArgumentException("报告类型仅支持 WEEKLY 或 MONTHLY");
        }
        return normalized;
    }
}
