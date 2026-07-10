package com.langxi.babydiary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "注册请求")
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名不能超过64个字符")
    @Schema(description = "用户名", example = "diary_user", required = true)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在6到64位之间")
    @Schema(description = "密码", example = "change-me-123", required = true)
    private String password;

    @NotBlank(message = "确认密码不能为空")
    @Size(min = 6, max = 64, message = "确认密码长度需在6到64位之间")
    @Schema(description = "确认密码", example = "change-me-123", required = true)
    private String confirmPassword;

    @NotBlank(message = "邀请码不能为空")
    @Size(max = 128, message = "邀请码长度无效")
    @Schema(description = "邀请码", example = "your-invitation-code", required = true)
    private String invitationCode;
}
