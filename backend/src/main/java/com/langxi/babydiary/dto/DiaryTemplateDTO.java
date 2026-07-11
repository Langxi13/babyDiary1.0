package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DiaryTemplateDTO {
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "模板说明不能超过500个字符")
    private String description;

    @Size(max = 32, message = "模板图标长度无效")
    private String icon;

    @Size(max = 1000, message = "引导问题不能超过1000个字符")
    private String promptText;

    @NotBlank(message = "模板内容不能为空")
    @Size(max = 100_000, message = "模板内容过长")
    private String contentHtml;
}
