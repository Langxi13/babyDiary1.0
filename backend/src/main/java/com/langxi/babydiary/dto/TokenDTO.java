package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TokenDTO {
    @NotBlank(message = "凭证不能为空")
    @Size(max = 512, message = "凭证长度无效")
    private String token;
}
