package com.langxi.babydiary.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MediaMetadataDTO {
    @Size(max = 500, message = "媒体说明不能超过500个字符")
    private String caption;

    @Size(max = 255, message = "地点名称不能超过255个字符")
    private String locationName;

    @DecimalMin(value = "-90.0", message = "纬度无效")
    @DecimalMax(value = "90.0", message = "纬度无效")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "经度无效")
    @DecimalMax(value = "180.0", message = "经度无效")
    private BigDecimal longitude;
}
