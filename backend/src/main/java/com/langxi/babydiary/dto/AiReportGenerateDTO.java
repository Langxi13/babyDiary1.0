package com.langxi.babydiary.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
public class AiReportGenerateDTO {
    @NotBlank(message = "报告类型不能为空")
    @Pattern(regexp = "WEEKLY|MONTHLY|ANNUAL", message = "报告类型仅支持WEEKLY、MONTHLY或ANNUAL")
    private String type;

    @NotBlank(message = "报告周期不能为空")
    @Size(max = 16, message = "报告周期格式无效")
    private String period;
}
