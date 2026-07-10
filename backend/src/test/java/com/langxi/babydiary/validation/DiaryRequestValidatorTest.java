package com.langxi.babydiary.validation;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiaryRequestValidatorTest {

    private final DiaryRequestValidator validator = new DiaryRequestValidator();

    @Test
    void diaryInputNormalizesSupportedFields() {
        DiaryRequestValidator.DiaryInput input = validator.diaryInput(
                "  今天  ", "正文", "2026-07-10", " HTML ", " happy ");

        assertThat(input.title()).isEqualTo("今天");
        assertThat(input.date().toString()).isEqualTo("2026-07-10");
        assertThat(input.contentFormat()).isEqualTo("html");
        assertThat(input.moodKey()).isEqualTo("happy");
    }

    @Test
    void invalidCalendarDateReturnsBadRequest() {
        assertThatThrownBy(() -> validator.diaryInput("标题", "正文", "2026-02-30", "html", null))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
                    assertThat(exception.getMessage()).contains("不是有效日期");
                });
    }

    @Test
    void yearZeroIsRejectedBeforeReachingMysql() {
        assertThatThrownBy(() -> validator.diaryInput("标题", "正文", "0000-01-01", "html", null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getMessage()).contains("年份范围无效"));
    }

    @Test
    void reversedDateRangeIsRejected() {
        assertThatThrownBy(() -> validator.requiredDateRange("2026-07-10", "2026-07-01"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getMessage()).contains("结束日期不能早于开始日期"));
    }

    @Test
    void excessiveTagsAreRejected() {
        String tagIds = IntStream.rangeClosed(1, 51)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));

        assertThatThrownBy(() -> validator.parseTagIds(tagIds))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getMessage()).contains("最多选择50个标签"));
    }

    @Test
    void pageSizeIsCappedAtOneHundred() {
        assertThat(validator.pageSize(10_000, 5)).isEqualTo(100);
    }
}
