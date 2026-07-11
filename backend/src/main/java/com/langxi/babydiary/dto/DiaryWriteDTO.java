package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DiaryWriteDTO {
    @Pattern(regexp = "[0-9a-fA-F-]{36}", message = "客户端日记ID格式无效")
    private String clientId;

    @NotBlank(message = "日记标题不能为空")
    @Size(max = 255, message = "日记标题不能超过255个字符")
    private String title;

    @NotBlank(message = "日记日期不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日记日期格式应为yyyy-MM-dd")
    private String date;

    @NotBlank(message = "日记内容不能为空")
    @Size(max = 1_000_000, message = "日记内容过长")
    private String content;

    @Pattern(regexp = "plain|html", message = "内容格式仅支持plain或html")
    private String contentFormat = "html";

    @Size(max = 32, message = "心情标识不能超过32个字符")
    private String moodKey;

    @Pattern(regexp = "PRIVATE|SHARED", message = "可见范围仅支持PRIVATE或SHARED")
    private String visibility;

    private Boolean locked;

    private Integer baseVersion;

    @Size(max = 50, message = "单篇日记最多选择50个标签")
    private List<Integer> tagIds;
}
