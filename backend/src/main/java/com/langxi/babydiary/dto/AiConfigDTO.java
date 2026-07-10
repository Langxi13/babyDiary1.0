package com.langxi.babydiary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiConfigDTO {
    private Boolean enabled;

    @Size(max = 255, message = "Base URL不能超过255个字符")
    private String baseUrl;

    @Size(max = 128, message = "模型名称不能超过128个字符")
    private String model;

    @Size(max = 4096, message = "API Key长度无效")
    private String apiKey;

    @Min(value = 5, message = "超时时间不能少于5秒")
    @Max(value = 120, message = "超时时间不能超过120秒")
    private Integer timeoutSeconds;
}
