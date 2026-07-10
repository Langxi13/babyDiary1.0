package com.langxi.babydiary.validation;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.common.Pagination;
import com.langxi.babydiary.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
public class DiaryRequestValidator {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_CONTENT_LENGTH = 1_000_000;
    private static final int MAX_KEYWORD_LENGTH = 200;
    private static final int MAX_MOOD_KEY_LENGTH = 32;
    private static final int MAX_TAGS = 50;
    private static final int MAX_IMAGES = 50;
    private static final LocalDate DEFAULT_START_DATE = LocalDate.of(2022, 1, 1);

    public DiaryInput diaryInput(String title, String content, String date,
                                 String contentFormat, String moodKey) {
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isEmpty()) {
            throw badRequest("日记标题不能为空");
        }
        if (normalizedTitle.length() > MAX_TITLE_LENGTH) {
            throw badRequest("日记标题不能超过255个字符");
        }
        String normalizedContent = content == null ? "" : content;
        if (normalizedContent.length() > MAX_CONTENT_LENGTH) {
            throw badRequest("日记内容过长");
        }
        String normalizedFormat = contentFormat == null ? "plain" : contentFormat.trim().toLowerCase(Locale.ROOT);
        if (!("plain".equals(normalizedFormat) || "html".equals(normalizedFormat))) {
            throw badRequest("内容格式仅支持plain或html");
        }
        return new DiaryInput(
                normalizedTitle,
                normalizedContent,
                Date.valueOf(parseDate(date, "日记日期")),
                normalizedFormat,
                normalizeMoodKey(moodKey)
        );
    }

    public DateRange diaryListRange(String startDate, String endDate) {
        LocalDate start = isBlank(startDate) ? DEFAULT_START_DATE : parseDate(startDate, "开始日期");
        LocalDate end = isBlank(endDate) ? LocalDate.now() : parseDate(endDate, "结束日期");
        return checkedRange(start, end);
    }

    public DateRange requiredDateRange(String startDate, String endDate) {
        return checkedRange(parseDate(startDate, "开始日期"), parseDate(endDate, "结束日期"));
    }

    public DateRange optionalDateRange(String startDate, String endDate) {
        LocalDate start = isBlank(startDate) ? null : parseDate(startDate, "开始日期");
        LocalDate end = isBlank(endDate) ? null : parseDate(endDate, "结束日期");
        if (start != null && end != null && end.isBefore(start)) {
            throw badRequest("结束日期不能早于开始日期");
        }
        return new DateRange(start == null ? null : start.toString(), end == null ? null : end.toString());
    }

    public String normalizeKeyword(String keyword) {
        if (isBlank(keyword)) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw badRequest("搜索关键字不能超过200个字符");
        }
        return normalized;
    }

    public String normalizeMoodKey(String moodKey) {
        if (isBlank(moodKey)) {
            return null;
        }
        String normalized = moodKey.trim();
        if (normalized.length() > MAX_MOOD_KEY_LENGTH) {
            throw badRequest("心情标识不能超过32个字符");
        }
        return normalized;
    }

    public List<Integer> parseTagIds(String tagIds) {
        if (tagIds == null) {
            return null;
        }
        if (tagIds.length() > 1000) {
            throw badRequest("标签参数过长");
        }
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (String item : tagIds.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int tagId = Integer.parseInt(trimmed);
                if (tagId <= 0) {
                    throw badRequest("标签ID无效");
                }
                result.add(tagId);
            } catch (NumberFormatException e) {
                throw badRequest("标签ID无效");
            }
            if (result.size() > MAX_TAGS) {
                throw badRequest("单篇日记最多选择50个标签");
            }
        }
        return new ArrayList<>(result);
    }

    public int pageSize(Integer requestedSize, int defaultSize) {
        return Pagination.normalizeSize(requestedSize == null ? defaultSize : requestedSize);
    }

    public void validateYearMonth(Integer year, Integer month) {
        if (year == null && month != null) {
            throw badRequest("选择月份时必须同时提供年份");
        }
        if (year != null && (year < 1 || year > 9999)) {
            throw badRequest("年份范围无效");
        }
        if (month != null && (month < 1 || month > 12)) {
            throw badRequest("月份范围应为1到12");
        }
    }

    public void validateImageCount(MultipartFile[] imageFiles) {
        if (imageFiles != null && imageFiles.length > MAX_IMAGES) {
            throw badRequest("单篇日记最多上传50张图片");
        }
    }

    public void validateImageReferences(List<String> retainedImagePaths, List<String> imageOrder) {
        validateStringList(retainedImagePaths, 100, 255, "保留图片参数无效");
        validateStringList(imageOrder, 100, 300, "图片排序参数无效");
    }

    private DateRange checkedRange(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw badRequest("结束日期不能早于开始日期");
        }
        return new DateRange(start.toString(), end.toString());
    }

    private void validateStringList(List<String> values, int maxItems, int maxLength, String message) {
        if (values == null) {
            return;
        }
        if (values.size() > maxItems || values.stream().anyMatch(value -> value == null || value.length() > maxLength)) {
            throw badRequest(message);
        }
    }

    private LocalDate parseDate(String value, String fieldName) {
        if (isBlank(value) || !value.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw badRequest(fieldName + "格式应为YYYY-MM-DD");
        }
        try {
            LocalDate parsed = LocalDate.parse(value.trim());
            if (parsed.getYear() < 1 || parsed.getYear() > 9999) {
                throw badRequest(fieldName + "年份范围无效");
            }
            return parsed;
        } catch (DateTimeParseException e) {
            throw badRequest(fieldName + "不是有效日期");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    public record DiaryInput(String title, String content, Date date, String contentFormat, String moodKey) {
    }

    public record DateRange(String startDate, String endDate) {
    }
}
