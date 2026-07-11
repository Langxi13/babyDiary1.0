package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StepUpDTO {
    @NotBlank(message = "请输入当前密码")
    @Size(max = 128, message = "密码长度无效")
    private String password;
}
