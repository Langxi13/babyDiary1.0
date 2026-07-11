package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetDTO {
    @NotBlank(message = "找回凭证不能为空")
    @Size(max = 512, message = "找回凭证长度无效")
    private String token;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度需在6到64位之间")
    private String newPassword;
}
