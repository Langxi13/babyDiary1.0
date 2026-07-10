package com.langxi.babydiary.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
public class AnniversaryDTO {
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过100个字符")
    private String title;

    @NotBlank(message = "日期不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式应为YYYY-MM-DD")
    private String date;

    @Size(max = 5000, message = "说明不能超过5000个字符")
    private String description;

    @Size(max = 255, message = "封面路径长度无效")
    private String coverImagePath;

    @Min(value = -100000, message = "排序值无效")
    @Max(value = 100000, message = "排序值无效")
    private Integer sort;
}
