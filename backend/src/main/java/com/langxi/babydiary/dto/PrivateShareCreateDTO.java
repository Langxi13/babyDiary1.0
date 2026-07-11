package com.langxi.babydiary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PrivateShareCreateDTO {
    @NotNull(message = "分享有效期不能为空")
    @Min(value = 1, message = "分享有效期至少1小时")
    @Max(value = 720, message = "分享有效期最多30天")
    private Integer expiresInHours = 24;

    @Size(min = 4, max = 64, message = "分享密码长度需为4到64位")
    private String password;

    @Min(value = 1, message = "浏览次数至少为1")
    @Max(value = 10000, message = "浏览次数上限过大")
    private Integer maxViews;
}
