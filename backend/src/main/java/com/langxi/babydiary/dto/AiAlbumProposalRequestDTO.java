package com.langxi.babydiary.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
public class AiAlbumProposalRequestDTO {
    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "开始日期格式应为YYYY-MM-DD")
    private String startDate;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "结束日期格式应为YYYY-MM-DD")
    private String endDate;

    @Size(max = 1000, message = "整理要求不能超过1000个字符")
    private String prompt;
}
