package com.langxi.babydiary.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
public class DiaryDraftDTO {
    @NotBlank(message = "草稿标识不能为空")
    @Size(max = 64, message = "草稿标识不能超过64个字符")
    private String draftKey;

    @Positive(message = "日记ID无效")
    private Integer diaryId;

    @Size(max = 255, message = "标题不能超过255个字符")
    private String title;

    @Pattern(regexp = "^$|\\d{4}-\\d{2}-\\d{2}", message = "日期格式应为YYYY-MM-DD")
    private String date;

    @Size(max = 1_000_000, message = "草稿内容过长")
    private String content;

    @Pattern(regexp = "plain|html", message = "内容格式仅支持plain或html")
    private String contentFormat;

    @Size(max = 32, message = "心情标识不能超过32个字符")
    private String moodKey;

    @Size(max = 50, message = "单篇日记最多选择50个标签")
    private List<@NotNull @Positive Integer> tagIds;
}
