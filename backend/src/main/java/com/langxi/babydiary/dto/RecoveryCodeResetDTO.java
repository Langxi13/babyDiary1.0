package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RecoveryCodeResetDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 255, message = "用户名长度无效")
    private String username;

    @NotBlank(message = "恢复码不能为空")
    @Size(max = 64, message = "恢复码长度无效")
    private String recoveryCode;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度需在6到64位之间")
    private String newPassword;
}
